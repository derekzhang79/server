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
package org.ohmage.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.ohmage.domain.CampaignUrnUserRole;
import org.ohmage.request.AwRequest;
import org.springframework.jdbc.core.RowMapper;


/**
 * @author selsky
 */
public class FindAllCampaignRolesForUserDao extends AbstractDao {
	private static Logger _logger = Logger.getLogger(FindAllCampaignRolesForUserDao.class);
	private boolean _useLoggedInUser;
	
	private String _sql = "SELECT ur.role, c.urn " +
			              "FROM user_role_campaign urc, user u, user_role ur, campaign c " +
			              "WHERE urc.campaign_id = c.id " +
			                "AND urc.user_id = u.id " +
			                "AND ur.id = urc.user_role_id " +
			                "AND u.username = ?";
	
	/**
	 * @param useLoggedInUser if true, the logged in user's user name will be used in the query. if false, the user name request
	 * parameter will be used (e.g., the data point API). 
	 */
	public FindAllCampaignRolesForUserDao(DataSource dataSource, boolean useLoggedInUser) {
		super(dataSource);
		_useLoggedInUser = useLoggedInUser;
	}
	
	@Override
	public void execute(AwRequest awRequest) {
		_logger.info("lookup up campaign roles for " + 
			(_useLoggedInUser ? awRequest.getUser().getUserName() : awRequest.getUserNameRequestParam()));
		
		try {
			awRequest.setResultList(
				getJdbcTemplate().query(
					_sql, 
					_useLoggedInUser ? new Object[] { awRequest.getUser().getUserName() } 
									 : new Object[] { awRequest.getUserNameRequestParam() },
					new RowMapper() { 
						public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
							CampaignUrnUserRole cuur = new CampaignUrnUserRole();
							cuur.setRole(rs.getString(1));
							cuur.setUrn(rs.getString(2));
							//_logger.info(cuur);
							return cuur;
						}
					}
				)
			);
		}	
		catch (org.springframework.dao.DataAccessException dae) {
			_logger.error("a DataAccessException occurred when running the following sql '" + _sql + "' with the parameter"
				+ (_useLoggedInUser ? awRequest.getUser().getUserName() : awRequest.getUserNameRequestParam()), dae);
			throw new DataAccessException(dae);
		}
	}
}