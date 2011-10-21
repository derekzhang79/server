package org.ohmage.validator;

import java.util.Date;

import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.exception.ValidationException;
import org.ohmage.util.StringUtils;

/**
 * This class is responsible for validating all values pertaining to 
 * visualization requests.
 * 
 * @author John Jenkins
 */
public class VisualizationValidators {
	// The width for 1080p.
	private static final int MAX_IMAGE_DIMENSION = 1920;
	
	/**
	 * Default constructor. Private so that it cannot be instantiated.
	 */
	private VisualizationValidators() {}
	
	/**
	 * Validates that the desired image width is not less than 0 and not  
	 * greater than {@value #MAX_IMAGE_DIMENSION}.
	 * 
	 * @param width The width to be validated.
	 * 
	 * @return Returns null if the width is null or whitespace only. Otherwise,
	 * 		   it returns a decoded value for the width.
	 * 
	 * @throws ValidationException Thrown if the width is not null, not 
	 * 							   whitespace only, and not a valid number or
	 * 							   a number that is out of bounds.
	 */
	public static Integer validateWidth(final String width) 
			throws ValidationException {
		
		if(StringUtils.isEmptyOrWhitespaceOnly(width)) {
			return null;
		}
		
		try {
			Integer result =  Integer.decode(width);
			
			if(result < 0) {
				throw new ValidationException(
						ErrorCode.VISUALIZATION_INVALID_WIDTH_VALUE, 
						"The image's width cannot be less than 0: " + result);
			}
			else if(result > MAX_IMAGE_DIMENSION) {
				throw new ValidationException(
						ErrorCode.VISUALIZATION_INVALID_WIDTH_VALUE, 
						"The image's width cannot be greater than " + 
							MAX_IMAGE_DIMENSION + 
							": " + 
							result);
			}
			else {
				return result;
			}
		}
		catch(NumberFormatException e) {
			throw new ValidationException(
					ErrorCode.VISUALIZATION_INVALID_WIDTH_VALUE, 
					"The image's width is not a valid number: " + width, 
					e);
		}
	}
	
	/**
	 * Validates that the desired image height is not less than 0 and not  
	 * greater than {@value #MAX_IMAGE_DIMENSION}.
	 * 
	 * @param height The height to be validated.
	 * 
	 * @return Returns null if the height is null or whitespace only. 
	 * 		   Otherwise, it returns a decoded value for the height.
	 * 
	 * @throws ValidationException Thrown if the height is not null, not 
	 * 							   whitespace only, and not a valid number or
	 * 							   a number that is out of bounds.
	 */
	public static Integer validateHeight(final String height) 
			throws ValidationException {
		if(StringUtils.isEmptyOrWhitespaceOnly(height)) {
			return null;
		}
		
		try {
			Integer result =  Integer.decode(height);
			
			if(result < 0) {
				throw new ValidationException(
						ErrorCode.VISUALIZATION_INVALID_WIDTH_VALUE, 
						"The image's width cannot be less than 0: " + result);
			}
			else if(result > MAX_IMAGE_DIMENSION) {
				throw new ValidationException(
						ErrorCode.VISUALIZATION_INVALID_WIDTH_VALUE, 
						"The image's width cannot be greater than " + 
							MAX_IMAGE_DIMENSION + 
							": " + 
							result);
			}
			else {
				return result;
			}
		}
		catch(NumberFormatException e) {
			throw new ValidationException(
					ErrorCode.VISUALIZATION_INVALID_WIDTH_VALUE, 
					"The image's width is not a valid number: " + height, 
					e);
		}
	}
	
	/**
	 * Validates that a start date string is a valid date and returns it. If it
	 * is not a valid date, the request is failed and an exception is thrown.
	 * 
	 * @param startDate The start date string to be validated.
	 * 
	 * @return Returns null the start date is null or whitespace only. 
	 * 		   Otherwise, the start date as a Date object is returned.
	 * 
	 * @throws ValidationException Thrown if the start date is not null, not
	 * 							   whitespace only, and not a valid date.
	 */
	public static Date validateStartDate(final String startDate) 
			throws ValidationException {
		
		if(StringUtils.isEmptyOrWhitespaceOnly(startDate)) {
			return null;
		}
		
		Date result = StringUtils.decodeDateTime(startDate);
		if(result == null) {
			result = StringUtils.decodeDate(startDate);
			
			if(result == null) {
				throw new ValidationException(
						ErrorCode.SERVER_INVALID_DATE, 
						"The start date is not a valid date: " + startDate);
			}
			else {
				return result;
			}
		}
		else {
			throw new ValidationException(
					ErrorCode.SERVER_INVALID_DATE, 
					"Only a date is allowed, not time: " + startDate);
		}
	}
	
	/**
	 * Validates that a end date string is a valid date and returns it. If it
	 * is not a valid date, the request is failed and an exception is thrown.
	 * 
	 * @param endDate The end date string to be validated.
	 * 
	 * @return Returns null the end date is null or whitespace only. Otherwise, 
	 * 		   the end date as a Date object is returned.
	 * 
	 * @throws ValidationException Thrown if the end date is not null, not
	 * 							   whitespace only, and not a valid date.
	 */
	public static Date validateEndDate(final String endDate) 
			throws ValidationException {
		
		if(StringUtils.isEmptyOrWhitespaceOnly(endDate)) {
			return null;
		}
		
		Date result = StringUtils.decodeDateTime(endDate);
		if(result == null) {
			result = StringUtils.decodeDate(endDate);
			
			if(result == null) {
				throw new ValidationException(
						ErrorCode.SERVER_INVALID_DATE, 
						"The end date is not a valid date: " + endDate);
			}
			else {
				return result;
			}
		}
		else {
			throw new ValidationException(
					ErrorCode.SERVER_INVALID_DATE, 
					"Only a date is allowed, not time: " + endDate);
		}
	}
}