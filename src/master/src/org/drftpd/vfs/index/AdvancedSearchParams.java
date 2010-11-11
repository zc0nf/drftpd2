/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.vfs.index;

import java.util.Collections;
import java.util.Set;

/**
 * @author fr0w
 * @version $Id$
 */
public class AdvancedSearchParams {
	
	public enum InodeType {
		DIRECTORY, FILE, ANY
	}

	private String _name;
	private boolean _exact = false;
	private String _endsWith;
	private InodeType _inodeType = InodeType.ANY;
	private Set<String> _slaves = Collections.emptySet();
	private String _user;
	private String _group;
	private Long _minAge;
	private Long _maxAge;
	private Long _minSize;
	private Long _maxSize;
	private Integer _minSlaves;
	private Integer _maxSlaves;
	private String _sortField;
	private boolean _sortOrder = false;
	private Integer _limit;

	public AdvancedSearchParams() {
		
	}

	public void setName(String name) {
		_name = name;
	}

	public void setExact(boolean exact) {
		_exact = exact;
	}

	public void setEndsWith(String endsWith) {
		_endsWith = endsWith;
	}

	public void setInodeType(InodeType type) {
		_inodeType = type;
	}
	
	public void setSlaves(Set<String> slaves) {
		_slaves = slaves;
	}
	
	public void setOwner(String user) {
		_user = user;
	}
	
	public void setGroup(String group) {
		_group = group;
	}

	public void setMinAge(long minAge) {
		_minAge = minAge;
	}

	public void setMaxAge(long maxAge) {
		_maxAge = maxAge;
	}

	public void setMinSize(long minSize) {
		_minSize = minSize;
	}

	public void setMaxSize(long maxSize) {
		_maxSize = maxSize;
	}

	public void setMinSlaves(int minSlaves) {
		_minSlaves = minSlaves;
	}

	public void setMaxSlaves(int maxSlaves) {
		_maxSlaves = maxSlaves;
	}

	public void setSortField(String sortField) {
		_sortField = sortField;
	}

	public void setSortOrder(boolean sortOrder) {
		_sortOrder = sortOrder;
	}

	public void setLimit(int limit) {
		_limit = limit;
	}

	public String getName() {
		return _name;
	}

	public boolean getExact() {
		return _exact;
	}

	public String getEndsWith() {
		return _endsWith;
	}
	
	public InodeType getInodeType() {
		return _inodeType;
	}
	
	public Set<String> getSlaves() {
		return _slaves;
	}
	
	public String getOwner() {
		return _user;
	}
	
	public String getGroup() {
		return _group;
	}

	public Long getMinAge() {
		return _minAge;
	}

	public Long getMaxAge() {
		return _maxAge;
	}

	public Long getMinSize() {
		return _minSize;
	}

	public Long getMaxSize() {
		return _maxSize;
	}

	public Integer getMinSlaves() {
		return _minSlaves;
	}

	public Integer getMaxSlaves() {
		return _maxSlaves;
	}

	public String getSortField() {
		return _sortField;
	}

	public boolean getSortOrder() {
		return _sortOrder;
	}

	public Integer getLimit() {
		return _limit;
	}
}
