/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.web.filter.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.util.DatabaseUpdater;
import org.openmrs.util.DatabaseUtil;
import org.openmrs.util.OpenmrsConstants;

/**
 * This class contains convenient methods for storing/retrieving locale parameters into/from DB as
 * admin's user property and as default locale property for OpenMRS system
 */
public class FilterUtil {
	
	private static final Log log = LogFactory.getLog(FilterUtil.class);
	
	public static final String LOCALE_ATTRIBUTE = "locale";
	
	public static final String REMEMBER_ATTRIBUTE = "remember";
	
	public static final String ADMIN_USERNAME = "admin";
	
	/**
	 * Tries to retrieve location parameter. First this method makes an attempt to load locale
	 * parameter as user's property. And next, if user's property is empty it tries to retrieve
	 * default system locale (i.e system global property). If it also is empty it uses default value
	 * for system locale
	 * 
	 * @param username the name of the administrative user whose default locale property will be
	 *            restored
	 * @return string with stored location parameter or default OpenMRS locale property's value
	 */
	public static String restoreLocale(String username) {
		String currentLocale = null;
		if (StringUtils.isNotBlank(username)) {
			Connection connection = null;
			try {
				connection = DatabaseUpdater.getConnection();
				
				// first we should try to get locale parameter as user's property
				Integer userId = getUserIdByName(username, connection);
				
				if (userId != null) {
					String select = "select property_value from user_property where user_id = ? and property = ?";
					PreparedStatement statement = connection.prepareStatement(select);
					statement.setInt(1, userId);
					statement.setString(2, OpenmrsConstants.USER_PROPERTY_DEFAULT_LOCALE);
					if (statement.execute()) {
						ResultSet results = statement.getResultSet();
						if (results.next())
							currentLocale = results.getString(1);
					}
				}
				
				// if locale is still null we should try to retrieve system locale global property's value
				if (currentLocale == null) {
					currentLocale = readSystemDefaultLocale(connection);
				}
			}
			catch (Exception e) {
				log.error("Error while retriving locale property", e);
			}
			finally {
				if (connection != null) {
					try {
						connection.close();
					}
					catch (SQLException e) {
						log.debug("Error while closing the database", e);
					}
				}
			}
		}
		// if locale is still null we just simply using default locale value (i.e. en_GB)
		if (currentLocale == null)
			currentLocale = OpenmrsConstants.GLOBAL_PROPERTY_DEFAULT_LOCALE_DEFAULT_VALUE;
		
		return currentLocale;
	}
	
	/**
	 * This method uses passed in connection to load system default locale. If connection is passed
	 * as null it creates separate connection that should be closed before return from method
	 * 
	 * @param connection (optional) the jdbc connection to be used for extracting default locale
	 * @return the string that contains system default locale or null
	 */
	public static String readSystemDefaultLocale(Connection connection) {
		String systemDefaultLocale = null;
		Boolean needToCloseConection = false;
		try {
			if (connection == null) {
				connection = DatabaseUpdater.getConnection();
				needToCloseConection = true;
			}
			String select = "select property_value from global_property where property = ?";
			PreparedStatement statement = connection.prepareStatement(select);
			statement.setString(1, OpenmrsConstants.GLOBAL_PROPERTY_DEFAULT_LOCALE);
			if (statement.execute()) {
				ResultSet results = statement.getResultSet();
				if (results.next())
					systemDefaultLocale = results.getString(1);
			}
		}
		catch (Exception e) {
			log.error("Error while retrieving system default locale", e);
		}
		finally {
			if (needToCloseConection) {
				if (connection != null)
					try {
						connection.close();
					}
					catch (SQLException e) {
						log.debug("Error while closing the database", e);
					}
			}
		}
		return systemDefaultLocale;
	}
	
	/**
	 * Stores selected by user locale into DB as admin's user property and as system default locale
	 * 
	 * @param locale the selected by user language
	 * @return true if locale was stored successfully
	 */
	public static boolean storeLocale(String locale) {
		if (StringUtils.isNotBlank(locale)) {
			
			Connection connection = null;
			Integer userId = null;
			try {
				connection = DatabaseUpdater.getConnection();
				
				// first we should try to get admin user id
				userId = getUserIdByName(ADMIN_USERNAME, connection);
				
				// first we are saving locale as administrative user's property
				if (userId != null) {
					String insert = "insert into user_property (user_id, property, property_value) values (?, 'defaultLocale', ?)";
					PreparedStatement statement = connection.prepareStatement(insert);
					statement.setInt(1, userId);
					statement.setString(2, locale);
					if (statement.executeUpdate() != 1) {
						log.warn("Unable to save user locale as admin property.");
					}
					
				}
				
				// and the second step is to save locale as system default locale global property
				String update = "update global_property set property_value = ? where property = ? ";
				PreparedStatement statement = connection.prepareStatement(update);
				statement.setString(1, locale);
				statement.setString(2, OpenmrsConstants.GLOBAL_PROPERTY_DEFAULT_LOCALE);
				if (statement.executeUpdate() != 1) {
					log.warn("Unable to set system default locale property.");
				}
			}
			catch (Exception e) {
				log.warn("Locale " + locale + " could not be set for user with id " + userId + " .", e);
				return false;
			}
			finally {
				if (connection != null) {
					try {
						connection.close();
					}
					catch (SQLException e) {
						log.debug("Error while closing the database", e);
					}
				}
			}
			return true;
		}
		return false;
	}
	
	/**
	 * This is a utility method that can be used for retrieving user id by given user name and sql
	 * connection
	 * 
	 * @param userNameOrSystemId the name of user
	 * @param connection the java sql connection to use
	 * @return not null id of given user in case of success or null otherwise
	 * @throws SQLException
	 */
	public static Integer getUserIdByName(String userNameOrSystemId, Connection connection) throws SQLException {
		
		String select = "select user_id from users where system_id = ? or username = ?";
		PreparedStatement statement = connection.prepareStatement(select);
		statement.setString(1, userNameOrSystemId);
		statement.setString(2, userNameOrSystemId);
		Integer userId = null;
		if (statement.execute()) {
			ResultSet results = statement.getResultSet();
			if (results.next())
				userId = results.getInt(1);
		}
		return userId;
	}
	
	/**
	 * Gets the value of a global Property as a string from the database using sql, this method is
	 * useful when you want to get a value of a global property before the application context has
	 * been setup
	 * 
	 * @param globalPropertyName the name of the global property
	 * @return the global property value
	 */
	public static String getGlobalPropertyValue(String globalPropertyName) {
		String propertyValue = null;
		Connection connection = null;
		
		try {
			connection = DatabaseUpdater.getConnection();
			List<List<Object>> results = DatabaseUtil.executeSQL(connection,
			    "select property_value from global_property where property = '" + globalPropertyName + "'", true);
			if (results.size() == 1 && results.get(0).size() == 1)
				propertyValue = results.get(0).get(0).toString();
		}
		catch (Exception e) {
			log.error("Error while retrieving value for global property:" + globalPropertyName, e);
		}
		finally {
			if (connection != null)
				try {
					connection.close();
				}
				catch (SQLException e) {
					log.debug("Error while closing the database connection", e);
				}
		}
		
		return propertyValue;
	}
}
