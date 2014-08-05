package org.ohmage.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.joda.time.DateTime;
import org.ohmage.bin.MediaBin;
import org.ohmage.bin.MultiValueResult;
import org.ohmage.bin.OhmletBin;
import org.ohmage.bin.SurveyBin;
import org.ohmage.bin.SurveyResponseBin;
import org.ohmage.domain.auth.AuthorizationToken;
import org.ohmage.domain.auth.Scope;
import org.ohmage.domain.exception.InsufficientPermissionsException;
import org.ohmage.domain.exception.InvalidArgumentException;
import org.ohmage.domain.exception.UnknownEntityException;
import org.ohmage.domain.survey.Media;
import org.ohmage.domain.survey.Survey;
import org.ohmage.domain.survey.SurveyResponse;
import org.ohmage.domain.user.User;
import org.ohmage.javax.servlet.filter.AuthFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * The controller for all requests for surveys and their responses.
 * </p>
 *
 * @author John Jenkins
 */
@Controller
@RequestMapping(SurveyController.ROOT_MAPPING)
public class SurveyController extends OhmageController {
    /**
     * The root API mapping for this Servlet.
     */
    public static final String ROOT_MAPPING = "/surveys";

    /**
     * The path and parameter key for survey IDs.
     */
    public static final String KEY_SURVEY_ID = "id";
    /**
     * The path and parameter key for survey versions.
     */
    public static final String KEY_SURVEY_VERSION = "version";
    /**
     * The parameter key for a survey definition.
     */
    public static final String KEY_SURVEY_DEFINITION = "definition";
    /**
     * The parameter for the icon.
     */
    public static final String KEY_ICON = "icon";
    /**
     * The name of the parameter for querying for specific values.
     */
    public static final String KEY_QUERY = "query";
    /**
     * The path and parameter key for survey point IDs.
     */
    public static final String KEY_SURVEY_RESPONSE_ID = "response_id";
    /**
     * The name of all media parts in multipart requests.
     */
    public static final String KEY_MEDIA = "media";

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER =
        LoggerFactory.getLogger(SurveyController.class.getName());

    /**
     * The usage in this class is entirely static, so there is no need to
     * instantiate it.
     */
    private SurveyController() {
        // Do nothing.
    }

    /**
     * Creates a new survey.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param surveyBuilder
     *        A builder to use to create this new survey.
     */
    @RequestMapping(value = { "", "/" }, method = RequestMethod.POST)
    public static @ResponseBody Survey createSurvey(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
        @RequestBody
            final Survey.Builder surveyBuilder) {

        return createSurvey(authToken, surveyBuilder, null);
    }

    /**
     * Creates a new survey.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param surveyBuilder
     *        A builder to use to create this new survey.
     *
     * @param iconFile
     *        If the survey definition references an icon, this should be the
     *        icon.
     */
    @RequestMapping(
        value = { "", "/" },
        method = RequestMethod.POST,
        consumes = "multipart/*")
    public static @ResponseBody Survey createSurvey(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
        @RequestPart(value = KEY_SURVEY_DEFINITION, required = true)
            final Survey.Builder surveyBuilder,
        @RequestPart(value = KEY_ICON, required = false)
            final MultipartFile iconFile) {

        LOGGER.info("Creating a survey creation request.");

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.debug("Setting the owner of the survey.");
        surveyBuilder.setOwner(user.getId());

        LOGGER.debug("Checking if an icon was given.");
        Media icon = null;
        // If given, verify that it was attached as well.
        String suppliedIconId = surveyBuilder.getIconId();
        if(suppliedIconId != null) {
            if(iconFile != null) {
                String origFileName = iconFile.getOriginalFilename();
                if(origFileName != null) {
                    if(origFileName.equals(suppliedIconId)) {
                        String newIconId = Media.generateUuid();
                        surveyBuilder.setIconId(newIconId);
                        icon = new Media(newIconId, iconFile);
                    } else {
                        throw
                            new InvalidArgumentException(
                                "Icon file's original name does not match supplied icon id.");
                    }
                } else {
                    throw
                        new InvalidArgumentException(
                            "Icon file has no original filename specified.");
                }
            } else {
                throw
                    new InvalidArgumentException(
                        "Icon ID provided but no icon file referenced.");
            }
        }
        LOGGER.debug("Building the updated survey.");
        Survey result = surveyBuilder.build();

        if(icon != null) {
            LOGGER.info("Storing the icon.");
            MediaBin.getInstance().addMedia(icon);
        }

        LOGGER.info("Saving the new survey.");
        SurveyBin.getInstance().addSurvey(result);

        LOGGER.info("Returning the updated survey.");
        return result;
    }

    /**
     * Returns a list of visible surveys.
     *
     * @param query
     *        A value that should appear in either the name or description.
     *
     * @param numToSkip
     *        The number of streams to skip.
     *
     * @param numToReturn
     *        The number of streams to return.
     *
     * @param rootUrl
     *        The root URL of the request. This should be of the form
     *        <tt>http[s]://{domain}[:{port}]{servlet_root_path}</tt>.
     *
     * @return A list of visible surveys.
     */
    @RequestMapping(value = { "", "/" }, method = RequestMethod.GET)
    public static @ResponseBody ResponseEntity<MultiValueResult<Survey>> getSurveys(
        @RequestParam(value = KEY_QUERY, required = false) final String query,
        @ModelAttribute(PARAM_PAGING_NUM_TO_SKIP) final long numToSkip,
        @ModelAttribute(PARAM_PAGING_NUM_TO_RETURN) final long numToReturn,
        @ModelAttribute(OhmageController.ATTRIBUTE_REQUEST_URL_ROOT)
            final String rootUrl) {

        LOGGER.info("Creating a survey ID read request.");

        LOGGER.info("Retrieving the stream IDs");
        MultiValueResult<Survey> ids =
            SurveyBin
                .getInstance()
                .getSurveys(query, false, numToSkip, numToReturn);

        LOGGER.info("Building the paging headers.");
        HttpHeaders headers =
            OhmageController
                .buildPagingHeaders(
                        numToSkip,
                        numToReturn,
                        Collections.<String, String>emptyMap(),
                        ids,
                        rootUrl + ROOT_MAPPING);

        LOGGER.info("Creating the response object.");
        ResponseEntity<MultiValueResult<Survey>> result =
            new ResponseEntity<MultiValueResult<Survey>>(
                ids,
                headers,
                HttpStatus.OK);

        LOGGER.info("Returning the stream IDs.");
        return result;
    }

    /**
     * Returns a list of versions for the given survey.
     *
     * @param surveyId
     *        The survey's unique identifier.
     *
     * @param query
     *        A value that should appear in either the name or description.
     *
     * @param numToSkip
     *        The number of stream IDs to skip.
     *
     * @param numToReturn
     *        The number of stream IDs to return.
     *
     * @param rootUrl
     *        The root URL of the request. This should be of the form
     *        <tt>http[s]://{domain}[:{port}]{servlet_root_path}</tt>.
     *
     * @return A list of the visible versions.
     */
    @RequestMapping(
        value = "{" + KEY_SURVEY_ID + "}",
        method = RequestMethod.GET)
    public static @ResponseBody ResponseEntity<MultiValueResult<Long>> getSurveyVersions(
        @PathVariable(KEY_SURVEY_ID) final String surveyId,
        @RequestParam(value = KEY_QUERY, required = false) final String query,
        @ModelAttribute(PARAM_PAGING_NUM_TO_SKIP) final long numToSkip,
        @ModelAttribute(PARAM_PAGING_NUM_TO_RETURN) final long numToReturn,
        @ModelAttribute(OhmageController.ATTRIBUTE_REQUEST_URL_ROOT)
            final String rootUrl) {

        LOGGER.info("Creating a request to read the versions of a survey: " +
                    surveyId);

        LOGGER.info("Validating the number to skip.");
        if(numToSkip < 0) {
            throw
                new InvalidArgumentException(
                    "The number to skip must be greater than or equal to 0.");
        }
        LOGGER.info("Validating the number to return.");
        if(numToReturn <= 0) {
            throw
                new InvalidArgumentException(
                    "The number to return must be greater than 0.");
        }
        LOGGER.info("Validating the upper bound of the number to return.");
        if(numToReturn > MAX_NUM_TO_RETURN) {
            throw
                new InvalidArgumentException(
                    "The number to return must be less than the upper limit " +
                        "of " +
                        MAX_NUM_TO_RETURN +
                        ".");
        }

        LOGGER.info("Retrieving the survey versions.");
        MultiValueResult<Long> versions =
            SurveyBin
                .getInstance()
                .getSurveyVersions(
                    surveyId,
                    query,
                    false,
                    numToSkip,
                    numToReturn);

        LOGGER.info("Building the paging headers.");
        HttpHeaders headers =
            OhmageController
                .buildPagingHeaders(
                        numToSkip,
                        numToReturn,
                        Collections.<String, String>emptyMap(),
                        versions,
                        rootUrl + ROOT_MAPPING);

        LOGGER.info("Creating the response object.");
        ResponseEntity<MultiValueResult<Long>> result =
            new ResponseEntity<MultiValueResult<Long>>(
                versions,
                headers,
                HttpStatus.OK);

        LOGGER.info("Returning the survey versions.");
        return result;
    }

    /**
     * Returns the definition for a given survey.
     *
     * @param surveyId
     *        The survey's unique identifier.
     *
     * @param surveyVersion
     *        The version of the survey.
     *
     * @return The survey definition.
     */
    @RequestMapping(
        value = "{" + KEY_SURVEY_ID + "}/{" + KEY_SURVEY_VERSION + "}",
        method = RequestMethod.GET)
    public static @ResponseBody Survey getSurveyDefinition(
        @PathVariable(KEY_SURVEY_ID) final String surveyId,
        @PathVariable(KEY_SURVEY_VERSION) final Long surveyVersion) {

        LOGGER.info("Creating a request for a survey definition: " +
                    surveyId + ", " +
                    surveyVersion);

        LOGGER.info("Retrieving the survey.");
        Survey result =
            SurveyBin
                .getInstance()
                .getSurvey(surveyId, surveyVersion, false);

        LOGGER.debug("Ensuring that a survey was found.");
        if(result == null) {
            throw
                new UnknownEntityException(
                    "The survey ID-verion pair is unknown.");
        }

        LOGGER.info("Returning the survey.");
        return result;
    }

    /**
     * Updates an existing survey with a new version.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param surveyId
     *        The survey's unique identifier.
     *
     * @param surveyBuilder
     *        A builder to use to create this new survey, which should be based
     *        on the survey that is being updated.
     */
    @RequestMapping(
        value = "{" + KEY_SURVEY_ID + "}",
        method = RequestMethod.POST)
    public static @ResponseBody Survey updateSurvey(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
        @PathVariable(KEY_SURVEY_ID) final String surveyId,
        @RequestBody final Survey.Builder surveyBuilder) {

        LOGGER.info("Creating a request to update a survey with a new version: " +
                    surveyId);

        LOGGER.info("Validating the user from the token");
        User user = OhmageController.validateAuthorization(authToken, null);

        LOGGER.info("Retrieving the latest version of the survey.");
        Survey latestSchema =
            SurveyBin.getInstance().getLatestSurvey(surveyId, false);

        // Increase survey version by 1
        surveyBuilder.setVersion(latestSchema.getVersion()+1);

        LOGGER.info("Verifying that the user updating the survey is the owner " +
                    "of the original survey.");
        if(! latestSchema.getOwner().equals(user.getId())) {
            throw
                new InsufficientPermissionsException(
                    "Only the owner of this survey may update it.");
        }

        LOGGER.debug("Setting the ID of the new survey to the ID of the old " +
                    "survey.");
        surveyBuilder.setSchemaId(surveyId);

        LOGGER.debug("Setting the request user as the owner of this new survey.");
        surveyBuilder.setOwner(user.getId());

        LOGGER.debug("Building the updated survey.");
        Survey result = surveyBuilder.build();

        LOGGER.info("Saving the updated survey.");
        SurveyBin.getInstance().addSurvey(result);

        LOGGER.info("Returning the updated survey.");
        return result;
    }

    /**
     * Stores data points.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param surveyId
     *        The survey's unique identifier.
     *
     * @param surveyVersion
     *        The version of the survey.
     *
     * @param surveyResponses
     *        The list of survey responses points to save.
     */
    @RequestMapping(
        value = "{" + KEY_SURVEY_ID + "}/{" + KEY_SURVEY_VERSION + "}/data",
        method = RequestMethod.POST)
    public static @ResponseBody MultiValueResult<? extends SurveyResponse> storeData(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
        @PathVariable(KEY_SURVEY_ID) final String surveyId,
        @PathVariable(KEY_SURVEY_VERSION) final Long surveyVersion,
        @RequestBody final SurveyResponse.Builder[] surveyResponses) {

        return
            storeData(
                authToken,
                surveyId,
                surveyVersion,
                surveyResponses,
                Collections.<MultipartFile>emptyList());
    }

    /**
     * Stores data points.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param surveyId
     *        The survey's unique identifier.
     *
     * @param surveyVersion
     *        The version of the survey.
     *
     * @param surveyResponses
     *        The list of survey responses points to save.
     *
     * @param media
     *        The binary media files that are referenced in the survey
     *        responses.
     */
    @RequestMapping(
        value = "{" + KEY_SURVEY_ID + "}/{" + KEY_SURVEY_VERSION + "}/data",
        method = RequestMethod.POST,
        consumes = "multipart/*")
    public static @ResponseBody MultiValueResult<? extends SurveyResponse> storeData(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
        @PathVariable(KEY_SURVEY_ID) final String surveyId,
        @PathVariable(KEY_SURVEY_VERSION) final Long surveyVersion,
        @RequestPart(SurveyResponse.JSON_KEY_DATA)
            final SurveyResponse.Builder[] surveyResponses,
        @RequestPart(value = KEY_MEDIA, required = false)
            final List<MultipartFile> media) {

        LOGGER.info("Storing some new survey data.");

        LOGGER.info("Validating the user from the token");
        User user =
            OhmageController
                .validateAuthorization(
                        authToken,
                        new Scope(
                                Scope.Type.SURVEY,
                                surveyId,
                                surveyVersion,
                                Scope.Permission.WRITE));

        LOGGER.info("Retrieving the survey.");
        Survey survey =
            SurveyBin
                .getInstance()
                .getSurvey(surveyId, surveyVersion, false);

        LOGGER.debug("Ensuring that a survey was found.");
        if(survey == null) {
            throw
                new UnknownEntityException(
                    "The survey ID-verion pair is unknown.");
        }

        LOGGER.debug("Building the media map.");
        Map<String, Media> mediaMap = new HashMap<String, Media>();
        for(MultipartFile part : media) {
            mediaMap
                .put(
                    part.getOriginalFilename(),
                    new Media(Media.generateUuid(), part));
        }

        LOGGER.info("Validating the survey responses.");
        Map<String, SurveyResponse> surveyResponseMap =
            new HashMap<String, SurveyResponse>(surveyResponses.length);
        for(SurveyResponse.Builder surveyResponseBuilder : surveyResponses) {
            // Set the user.
            surveyResponseBuilder.setOwner(user.getId());

            // Build the survey response.
            SurveyResponse surveyResponse =
                surveyResponseBuilder
                    .build(survey, mediaMap);

            // Add the survey response to its map.
            surveyResponseMap.put(surveyResponse.getId(), surveyResponse);
        }
        Set<String> surveyResponseIds =
            new HashSet<String>(surveyResponseMap.keySet());

        LOGGER.info("Retrieving the duplicate IDs.");
        MultiValueResult<String> duplicateSurveyResponseIds =
            SurveyResponseBin
                .getInstance()
                .getDuplicateIds(
                    user.getId(),
                    surveyId,
                    surveyVersion,
                    surveyResponseIds);
        LOGGER.info("There are " +
                    duplicateSurveyResponseIds.size() +
                    " duplicates.");


        // TODO -- handle the case where every response is a duplicate

        LOGGER.info("Removing the duplicate survey responses.");
        List<String> duplicateResponseMediaIds = new LinkedList<String>();
        for(String duplicateId : duplicateSurveyResponseIds) {
            SurveyResponse duplicate = surveyResponseMap.remove(duplicateId);
            duplicateResponseMediaIds.addAll(duplicate.getMediaIds());
        }
        List<SurveyResponse> surveyResponseList =
            new ArrayList<SurveyResponse>(surveyResponseMap.values());

        LOGGER.info("Storing the media files.");
        for(Media currMedia : mediaMap.values()) {
            if(! duplicateResponseMediaIds.contains(currMedia.getId())) {
                MediaBin.getInstance().addMedia(currMedia);
            }
        }

        LOGGER.debug("Adding the user's unqiue identifier to the list of " +
                    "identifiers to query.");
        Set<String> userIds = new HashSet<String>();
        userIds.add(user.getId());

        LOGGER.info("Storing the validated survey responses.");
        SurveyResponseBin.getInstance().addSurveyResponses(surveyResponseList);

        LOGGER.trace("Returning the list of saved survey responses.");
        return
            SurveyResponseBin
                .getInstance()
                .getSurveyResponses(
                    surveyId,
                    surveyVersion,
                    userIds,
                    surveyResponseIds,
                    null,
                    null,
                    null,
                    true,
                    0,
                    Long.MAX_VALUE);
    }

    /**
     * Retrieves the data for the requesting user.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param surveyId
     *        The unique identifier of the survey whose data is being
     *        requested.
     *
     * @param surveyVersion
     *        The version of the survey whose data is being requested.
     *
     * @param startDate
     *        The earliest date for a given point.
     *
     * @param endDate
     *        The latest date for a given point.
     *
     * @param chronological
     *        Whether or not the data should be sorted in chronological order
     *        (as opposed to reverse-chronological order).
     *
     * @param numToSkip
     *        The number of stream IDs to skip.
     *
     * @param numToReturn
     *        The number of stream IDs to return.
     *
     * @param rootUrl
     *        The root URL of the request. This should be of the form
     *        <tt>http[s]://{domain}[:{port}]{servlet_root_path}</tt>.
     *
     * @return The data that conforms to the request parameters.
     */
    @RequestMapping(
        value = "{" + KEY_SURVEY_ID + "}/{" + KEY_SURVEY_VERSION + "}/data",
        method = RequestMethod.GET)
    public static @ResponseBody ResponseEntity<MultiValueResult<? extends SurveyResponse>> getData(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
        @PathVariable(KEY_SURVEY_ID) final String surveyId,
        @PathVariable(KEY_SURVEY_VERSION) final Long surveyVersion,
        @RequestParam(value = PARAM_START_DATE, required = false)
            final String startDate,
        @RequestParam(value = PARAM_END_DATE, required = false)
            final String endDate,
        @RequestParam(
            value = PARAM_CHRONOLOGICAL,
            required = false,
            defaultValue = PARAM_DEFAULT_CHRONOLOGICAL)
            final boolean chronological,
        @ModelAttribute(PARAM_PAGING_NUM_TO_SKIP) final long numToSkip,
        @ModelAttribute(PARAM_PAGING_NUM_TO_RETURN) final long numToReturn,
        @ModelAttribute(OhmageController.ATTRIBUTE_REQUEST_URL_ROOT)
            final String rootUrl) {

        LOGGER.info("Retrieving some survey responses.");

        LOGGER.info("Validating the user from the token.");
        User user =
            OhmageController
                .validateAuthorization(
                        authToken,
                        new Scope(
                                Scope.Type.SURVEY,
                                surveyId,
                                surveyVersion,
                                Scope.Permission.READ));

        LOGGER.debug("Parsing the start and end dates, if given.");
        DateTime startDateObject =
            (startDate == null) ?
                null :
                OHMAGE_DATE_TIME_FORMATTER.parseDateTime(startDate);
        DateTime endDateObject =
            (endDate == null) ?
                null :
                OHMAGE_DATE_TIME_FORMATTER.parseDateTime(endDate);

        LOGGER.info("Retrieving the latest versino of the survey.");
        Survey latestSurvey =
            SurveyBin.getInstance().getLatestSurvey(surveyId, false);
        if(latestSurvey == null) {
            throw new UnknownEntityException("The survey is unknown.");
        }

        LOGGER.debug("Determining if the user is asking about the latest version " +
                    "of the stream.");
        boolean allowNull = latestSurvey.getVersion() == surveyVersion;

        LOGGER.info("Gathering the applicable ohmlets.");
        Set<String> ohmletIds =
            OhmletBin
                .getInstance()
                .getOhmletIdsWhereUserCanReadSurveyResponses(
                    user.getId(),
                    surveyId,
                    surveyVersion,
                    allowNull);

        LOGGER.info("Determining which users are visible to the requesting user.");
        Set<String> userIds;
        if(authToken.getAuthorizationCode() == null) {
            LOGGER.info("The auth token was granted directly to the requesting " +
                        "user; retrieving the list of user IDs that are " +
                        "visible to the requesting user.");
            userIds = OhmletBin.getInstance().getMemberIds(ohmletIds);
        }
        else {
            LOGGER.info("The auth token was granted via OAuth, so only the user " +
                        "reference by the token may be searched.");
            userIds = new HashSet<String>();
            userIds.add(user.getId());
        }

        LOGGER.info("Finding the requested data.");
        MultiValueResult<? extends SurveyResponse> data =
            SurveyResponseBin
                .getInstance()
                .getSurveyResponses(
                    surveyId,
                    surveyVersion,
                    userIds,
                    null,
                    startDateObject,
                    endDateObject,
                    null,
                    chronological,
                    numToSkip,
                    numToReturn);

        LOGGER.info("Building the paging headers.");
        HttpHeaders headers =
            OhmageController
                .buildPagingHeaders(
                        numToSkip,
                        numToReturn,
                        Collections.<String, String>emptyMap(),
                        data,
                        rootUrl + ROOT_MAPPING);

        LOGGER.info("Creating the response object.");
        ResponseEntity<MultiValueResult<? extends SurveyResponse>> result =
            new ResponseEntity<MultiValueResult<? extends SurveyResponse>>(
                data,
                headers,
                HttpStatus.OK);

        LOGGER.info("Finding and returning the requested data.");
        return result;
    }

    /**
     * Retrieves a point.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param surveyId
     *        The unique identifier of the survey whose data is being
     *        requested.
     *
     * @param surveyVersion
     *        The version of the survey whose data is being requested.
     *
     * @param pointId
     *        The unique identifier for a specific point.
     *
     * @return The data that conforms to the request parameters.
     */
    @RequestMapping(
        value =
            "{" + KEY_SURVEY_ID + "}" +
            "/" +
            "{" + KEY_SURVEY_VERSION + "}" +
            "/data" +
            "/" +
            "{" + KEY_SURVEY_RESPONSE_ID + "}",
        method = RequestMethod.GET)
    public static @ResponseBody SurveyResponse getPoint(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
        @PathVariable(KEY_SURVEY_ID) final String surveyId,
        @PathVariable(KEY_SURVEY_VERSION) final Long surveyVersion,
        @PathVariable(KEY_SURVEY_RESPONSE_ID) final String pointId) {

        LOGGER.info("Retrieving a specific survey data point.");

        LOGGER.info("Validating the user from the token.");
        User user =
            OhmageController
                .validateAuthorization(
                        authToken,
                        new Scope(
                                Scope.Type.SURVEY,
                                surveyId,
                                surveyVersion,
                                Scope.Permission.READ));

        LOGGER.info("Returning the survey data.");
        return
            SurveyResponseBin
                .getInstance()
                .getSurveyResponse(
                    user.getId(),
                    surveyId,
                    surveyVersion,
                    pointId);
    }

    /**
     * Deletes a point.
     *
     * @param authToken
     *        The authorization information corresponding to the user that is
     *        making this call.
     *
     * @param surveyId
     *        The unique identifier of the survey whose data is being
     *        requested.
     *
     * @param surveyVersion
     *        The version of the survey whose data is being requested.
     *
     * @param pointId
     *        The unique identifier for a specific point.
     */
    @RequestMapping(
        value =
            "{" + KEY_SURVEY_ID + "}" +
            "/" +
            "{" + KEY_SURVEY_VERSION + "}" +
            "/data" +
            "/" +
            "{" + KEY_SURVEY_RESPONSE_ID + "}",
        method = RequestMethod.DELETE)
    public static @ResponseBody void deletePoint(
        @ModelAttribute(AuthFilter.ATTRIBUTE_AUTH_TOKEN)
            final AuthorizationToken authToken,
        @PathVariable(KEY_SURVEY_ID) final String surveyId,
        @PathVariable(KEY_SURVEY_VERSION) final Long surveyVersion,
        @PathVariable(KEY_SURVEY_RESPONSE_ID) final String pointId) {

        LOGGER.info("Deleting a specific survey data point.");

        LOGGER.info("Validating the user from the token.");
        User user =
            OhmageController
                .validateAuthorization(
                        authToken,
                        new Scope(
                                Scope.Type.SURVEY,
                                surveyId,
                                surveyVersion,
                                Scope.Permission.DELETE));

        LOGGER.info("Deleting the survey data.");
        SurveyResponseBin
            .getInstance()
            .deleteSurveyResponse(
                user.getId(),
                surveyId,
                surveyVersion,
                pointId);
    }
}
