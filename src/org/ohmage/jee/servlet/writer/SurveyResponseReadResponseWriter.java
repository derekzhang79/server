/*******************************************************************************
 * Copyright 2011 The Regents of the University of California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.ohmage.jee.servlet.writer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ohmage.domain.CustomChoiceItem;
import org.ohmage.domain.ErrorResponse;
import org.ohmage.domain.PromptResponseMetadata;
import org.ohmage.domain.SurveyResponseReadIndexedResult;
import org.ohmage.domain.SurveyResponseReadResult;
import org.ohmage.request.AwRequest;
import org.ohmage.request.InputKeys;
import org.ohmage.request.SurveyResponseReadAwRequest;
import org.ohmage.util.CookieUtils;
import org.ohmage.util.JsonUtils;


/** 
 * Builds survey response read output by dispatching to different output builders depending on the value of the output_format
 * provided in the request.
 * 
 * @author Joshua Selsky
 */
public class SurveyResponseReadResponseWriter extends AbstractResponseWriter {
	private static Logger _logger = Logger.getLogger(SurveyResponseReadResponseWriter.class);
	private SurveyResponseReadCsvOutputBuilder _csvOutputBuilder;
	private SurveyResponseReadJsonColumnOutputBuilder _jsonColumnOutputBuilder;
	private SurveyResponseReadJsonRowBasedOutputBuilder _jsonRowBasedOutputBuilder;
	
	private List<String> _columnNames;
	
	private static final int MAGIC_CUSTOM_CHOICE_INDEX = 100;
	
	public SurveyResponseReadResponseWriter(ErrorResponse errorResponse, 
			                               List<String> columnNames,
			                               SurveyResponseReadJsonRowBasedOutputBuilder rowBasedOutputBuilder,
			                               SurveyResponseReadJsonColumnOutputBuilder jsonColumnOutputBuilder,
			                               SurveyResponseReadCsvOutputBuilder csvOutputBuilder) {
		super(errorResponse);
		if(null == columnNames || columnNames.size() == 0) {
			throw new IllegalArgumentException("A non-null, non-empty columnNames list is required");
		}
		if(null == rowBasedOutputBuilder) {
			throw new IllegalArgumentException("A non-null SurveyResponseReadJsonRowBasedOutputBuilder is required");
		}
		if(null == jsonColumnOutputBuilder) {
			throw new IllegalArgumentException("A non-null SurveyResponseReadJsonColumnOutputBuilder is required");
		}
		if(null == csvOutputBuilder) {
			throw new IllegalArgumentException("A non-null SurveyResponseReadCsvColumnOutputBuilder is required");
		}
		
		_columnNames = columnNames;
		_jsonRowBasedOutputBuilder = rowBasedOutputBuilder;
		_jsonColumnOutputBuilder = jsonColumnOutputBuilder;
		_csvOutputBuilder = csvOutputBuilder;
	}
	
	/**
	 * Performs a sort on the query results in order to "roll up" interleaved prompt responses into their associated prompt
	 * response and then dispatches to the appropriate output builder to generate output. Finally, writes the output
	 * to the HttpServletResponse's output stream.
	 */
	@Override
	public void write(HttpServletRequest request, HttpServletResponse response, AwRequest awRequest) {
		Writer writer = null;
		SurveyResponseReadAwRequest req = (SurveyResponseReadAwRequest) awRequest;
		
		try {
			// Prepare for sending the response to the client
			writer = new BufferedWriter(new OutputStreamWriter(getOutputStream(request, response)));
			String responseText = null;
			
			// Sets the HTTP headers to disable caching
			expireResponse(response);
			CookieUtils.setCookieValue(response, InputKeys.AUTH_TOKEN, awRequest.getUserToken(), AUTH_TOKEN_COOKIE_LIFETIME_IN_SECONDS);
			
			// Set the content type
			if("csv".equals(req.getOutputFormat())) {
				response.setContentType("text/csv");
				response.setHeader("Content-Disposition", "attachment; f.txt");
			} else {
				response.setContentType("application/json");
			}
			
			// Build the appropriate response 
			if(! awRequest.isFailedRequest()) {
				
				List<String> columnList = req.getColumnList();
				List<String> outputColumns = new ArrayList<String>();
				@SuppressWarnings("unchecked")
				List<SurveyResponseReadResult> results = (List<SurveyResponseReadResult>) req.getResultList();
				List<SurveyResponseReadIndexedResult> indexedResults = new ArrayList<SurveyResponseReadIndexedResult>();
				
				// Build the column headers
				// Each column is a Map with a list containing the values for each row
				
				if("urn:ohmage:special:all".equals(columnList.get(0))) {
					outputColumns.addAll(_columnNames);
				} else {
					outputColumns.addAll(columnList);
				}
				
				if(columnList.contains("urn:ohmage:prompt:response") || "urn:ohmage:special:all".equals(columnList.get(0))) {
					// The logic here is that if the user is requesting results for survey ids, they want all of the prompts
					// for those survey ids
					// So, loop through the results and find all of the unique prompt ids by forcing them into a Set
					Set<String> promptIdSet = new HashSet<String>();
					
					if(0 != results.size()) {
						for(SurveyResponseReadResult result : results) {
							
							promptIdSet.add("urn:ohmage:prompt:id:" + result.getPromptId());
						}
						outputColumns.addAll(promptIdSet);
					}
				}
				
				// get rid of urn:ohmage:prompt:response because it has been replaced with specific prompt ids
				// the list will be unchanged if it didn't already contain urn:ohmage:prompt:response 
				outputColumns.remove("urn:ohmage:prompt:response");
				
				
				// For every result found by the query, the prompt responses need to be rolled up so they are all stored
				// with their associated survey response and metadata. Each prompt response is returned from the db in its
				// own row and the rows can have different sort orders.
				
				boolean isCsv = "csv".equals(req.getOutputFormat());
				
				for(SurveyResponseReadResult result : results) {
					
					if(indexedResults.isEmpty()) { // first time thru 
						indexedResults.add(new SurveyResponseReadIndexedResult(result, isCsv));
					}
					else {
						int numberOfIndexedResults = indexedResults.size();
						boolean found = false;
						for(int i = 0; i < numberOfIndexedResults; i++) {
							if(indexedResults.get(i).getKey().keysAreEqual(result.getUsername(),
									                                       result.getTimestamp(),
									                                       result.getSurveyId(),
									                                       result.getRepeatableSetId(),
									                                       result.getRepeatableSetIteration())) {
								
								found = true;
								indexedResults.get(i).addPromptResponse(result, isCsv);
							}
						}
						if(! found) {
							indexedResults.add(new SurveyResponseReadIndexedResult(result, isCsv));
						}
					}
				}
				
				// For csv and json-columns output, the custom choices need to be converted
				// into unique-ified list in order for visualiztions and export to work
				// properly. The key is the prompt id.
				Map<String, List<CustomChoiceItem>> uniqueCustomChoiceMap = null; // will be converted into a choice glossary
				                                                                  // for the custom types
				Map<String, Integer> uniqueCustomChoiceIndexMap = null;
				
				// Now find the custom choice prompts (if there are any) and 
				// unique-ify the entries for their choice glossaries, create 
				// their choice glossaries, and clean up the display value 
				// (i.e., remove all the custom_choices stuff and leave only
				// the value or values the user selected).
					
				for(SurveyResponseReadIndexedResult result : indexedResults) {
					Map<String, PromptResponseMetadata> promptResponseMetadataMap = result.getPromptResponseMetadataMap();
					Iterator<String> responseMetadataKeyIterator = promptResponseMetadataMap.keySet().iterator();
					
					while(responseMetadataKeyIterator.hasNext()) {
						String promptId = (responseMetadataKeyIterator.next());
						PromptResponseMetadata metadata = promptResponseMetadataMap.get(promptId);
						
						if("single_choice_custom".equals(metadata.getPromptType()) || "multi_choice_custom".equals(metadata.getPromptType())) {
							
							List<CustomChoiceItem> customChoiceItems = null;
							
							if(null == uniqueCustomChoiceMap) { // lazily initialized in case there are no custom choices
								uniqueCustomChoiceMap = new HashMap<String, List<CustomChoiceItem>>();
							} 
							
							if(! uniqueCustomChoiceMap.containsKey(promptId)) {
								customChoiceItems = new ArrayList<CustomChoiceItem>();
								uniqueCustomChoiceMap.put(promptId, customChoiceItems);
							} 
							else {
								customChoiceItems = uniqueCustomChoiceMap.get(promptId);
							}
							
							
							String tmp = (String) result.getPromptResponseMap().get(promptId);
							
							if(! ("SKIPPED".equals(tmp) || "NOT_DISPLAYED".equals(tmp))) {
								// All of the data for the choice_glossary for custom types is stored in its JSON response
									JSONObject customChoiceResponse = new JSONObject((String) result.getPromptResponseMap().get(promptId));
 
									// Since the glossary will not contain the custom choices, the result's display value 
								// can simply be the values the user chose.
								// The value will either be a string (single_choice_custom) or an array (multi_choice_custom)
								Integer singleChoiceValue = JsonUtils.getIntegerFromJsonObject(customChoiceResponse, "value");
								if(null != singleChoiceValue) {
									result.getPromptResponseMap().put(promptId, singleChoiceValue);
								}
								else {
									result.getPromptResponseMap().put(promptId, JsonUtils.getJsonArrayFromJsonObject(customChoiceResponse, "value"));
								}
								
								JSONArray customChoices = JsonUtils.getJsonArrayFromJsonObject(customChoiceResponse, "custom_choices");
								
								
								for(int i = 0; i < customChoices.length(); i++) {
									JSONObject choice = JsonUtils.getJsonObjectFromJsonArray(customChoices, i);

									// If the choice_id is >= 100, it means that is it a choice that the user added
									// In the current system, users cannot remove choices
									int originalId = choice.getInt("choice_id");
									CustomChoiceItem cci = null;
									boolean isGlobal = false;
									if(originalId < MAGIC_CUSTOM_CHOICE_INDEX) {										
										cci = new CustomChoiceItem(originalId, result.getUsername(), choice.getString("choice_value"), "global");
										isGlobal = true;
									} 
									else {
										cci = new CustomChoiceItem(originalId, result.getUsername(), choice.getString("choice_value"), "custom");
									}
									
									if(! customChoiceItems.contains(cci)) {
										if(isGlobal) {
											cci.setId(cci.getOriginalId());
											customChoiceItems.add(cci);
										}
										else {
											if(null == uniqueCustomChoiceIndexMap) {
												uniqueCustomChoiceIndexMap = new HashMap<String, Integer>();												
											}
											
											if(! uniqueCustomChoiceIndexMap.containsKey(promptId)) {
												uniqueCustomChoiceIndexMap.put(promptId, MAGIC_CUSTOM_CHOICE_INDEX - 1);
											}
											
											int uniqueId = uniqueCustomChoiceIndexMap.get(promptId) + 1;
											cci.setId(uniqueId);
											customChoiceItems.add(cci);
											uniqueCustomChoiceIndexMap.put(promptId, uniqueId);
										}
									}	
								}
							}
						}
					}
 				}
				
				int numberOfSurveys = indexedResults.size();
				int numberOfPrompts = results.size();
				
				// Delete the original result list
				results.clear();
				results = null;
				
				if("json-rows".equals(req.getOutputFormat())) {
					responseText = _jsonRowBasedOutputBuilder.buildOutput(numberOfSurveys, numberOfPrompts, req, indexedResults, outputColumns, uniqueCustomChoiceMap);
				}
				else if("json-columns".equals(req.getOutputFormat())) {
					if(indexedResults.isEmpty()) {
						responseText = _jsonColumnOutputBuilder.createZeroResultOutput(req, outputColumns);
					} else {
						responseText = _jsonColumnOutputBuilder.createMultiResultOutput(numberOfSurveys, numberOfPrompts, req, indexedResults, outputColumns, uniqueCustomChoiceMap);
					}
				}
				else if("csv".equals(req.getOutputFormat())) {
					if(indexedResults.isEmpty()) {
						responseText = _csvOutputBuilder.createZeroResultOutput(req, outputColumns);
					} else {
						responseText = _csvOutputBuilder.createMultiResultOutput(numberOfSurveys, numberOfPrompts, req, indexedResults, outputColumns, uniqueCustomChoiceMap);
					}
				}
			} 
			else {
				// Even for CSV output, the error messages remain JSON
				
				if(null != awRequest.getFailedRequestErrorMessage()) {
					responseText = awRequest.getFailedRequestErrorMessage();
				} else {
					responseText = generalJsonErrorMessage();
				}
			}
			
			_logger.info("Generating survey response read output.");
			writer.write(responseText);
		}
		
		catch(Exception e) { // catch Exception in order to avoid redundant catch block functionality
			
			_logger.error("An unrecoverable exception occurred while generating a response", e);
			try {
				writer.write(generalJsonErrorMessage());
			} catch (Exception ee) {
				_logger.error("Caught Exception when attempting to write to HTTP output stream", ee);
			}
			
		} finally {
			if(null != writer) {
				try {
					writer.flush();
					writer.close();
					writer = null;
				} catch (IOException ioe) {
					_logger.error("Caught IOException when attempting to free resources", ioe);
				}
			}
		}
	}
}