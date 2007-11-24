/*
 This file is part of BORG.
 
 BORG is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.
 
 BORG is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 
 You should have received a copy of the GNU General Public License
 along with BORG; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 
 Copyright 2004 by Mohan Embar - http://www.thisiscool.com/
 */

package net.sf.borg.model.db.remote.server;

import java.util.HashMap;
import java.util.Map;

import net.sf.borg.common.Errmsg;
import net.sf.borg.common.XTree;
import net.sf.borg.model.BorgOption;
import net.sf.borg.model.beans.KeyedBean;
import net.sf.borg.model.beans.Project;
import net.sf.borg.model.beans.Subtask;
import net.sf.borg.model.beans.Tasklog;
import net.sf.borg.model.db.AppointmentDB;
import net.sf.borg.model.db.BeanDB;
import net.sf.borg.model.db.BeanDataFactoryFactory;
import net.sf.borg.model.db.DBException;
import net.sf.borg.model.db.IBeanDataFactory;
import net.sf.borg.model.db.MultiUserDB;
import net.sf.borg.model.db.TaskDB;
import net.sf.borg.model.db.remote.IRemoteProxy;
import net.sf.borg.model.db.remote.XmlObjectHelper;

/**
 * The server-side component of the BORG remote invocation framework. This is
 * designed for single-threaded invocation only.
 */
public class BorgHandler {
    public BorgHandler(String url) {
	this.url = url;
    }

    public final String execute(String strXml, String username, String sessionId) {
	Object result = null;

	// System.out.println("[INPUT] "+strXml);
	// System.out.println("User: "+username);
	// System.out.println("Session ID: "+sessionId);

	try {
	    XTree xmlParms = XTree.readFromBuffer(strXml);
	    IRemoteProxy.Parms parms = (IRemoteProxy.Parms) XmlObjectHelper
		    .fromXml(xmlParms);

	    // Figure out what we need to do
	    String uid = parms.getUser();
	    if (uid.equals("$default"))
		uid = username;
	    BeanDB beanDB = getBeanDB(parms, uid);
	    String cmd = parms.getCommand();

	// 
	// Remote DB
	//
	    if (cmd.equals("readAll")) {
		result = beanDB.readAll();
	    } else if (cmd.equals("readObj")) {
		int key = ((Integer) parms.getArgs()).intValue();
		result = beanDB.readObj(key);
	    } else if (cmd.equals("delete")) {
		int key = ((Integer) parms.getArgs()).intValue();
		beanDB.delete(key);
		touch(sessionId);
	    } else if (cmd.equals("getOption")) {
		String key = (String) parms.getArgs();
		result = beanDB.getOption(key);
	    } else if (cmd.equals("getOptions")) {
		result = beanDB.getOptions();
	    } else if (cmd.equals("nextkey")) {
		result = new Integer(beanDB.nextkey());
	    } else if (cmd.equals("isDirty")) {
		result = new Boolean(isDirty(parms, sessionId));
	    } else if (cmd.equals("setOption")) {
		BorgOption option = (BorgOption) parms.getArgs();
		beanDB.setOption(option);
		touch(sessionId);
	    } else if (cmd.equals("addObj") || cmd.equals("updateObj")) {
		IRemoteProxy.ComposedObject agg = (IRemoteProxy.ComposedObject) parms
			.getArgs();
		KeyedBean bean = (KeyedBean) agg.getO1();
		boolean crypt = ((Boolean) agg.getO2()).booleanValue();

		if (cmd.equals("addObj"))
		    beanDB.addObj(bean, crypt);
		else
		    beanDB.updateObj(bean, crypt);

		touch(sessionId);
	//
	// Appointment DB specific
	//
	    } else if (cmd.equals("getTodoKeys")) {
		result = ((AppointmentDB) beanDB).getTodoKeys();
	    } else if (cmd.equals("getRepeatKeys")) {
		result = ((AppointmentDB) beanDB).getRepeatKeys();
	    } else if (cmd.equals("getAllUsers")
		    && beanDB instanceof MultiUserDB) {
		result = ((MultiUserDB) beanDB).getAllUsers();
        //
        // Task DB Specific
	//
	    } else if (cmd.equals("addLog")) {
		IRemoteProxy.ComposedObject agg = (IRemoteProxy.ComposedObject) parms.getArgs();
		Integer id = (Integer) agg.getO1();
		((TaskDB) beanDB).addLog(id.intValue(),(String)agg.getO2());
		touch(sessionId);
	    } else if (cmd.equals("addProject")) {
		((TaskDB) beanDB).addProject((Project)parms.getArgs());
		touch(sessionId);
	    } else if (cmd.equals("addSubTask")) {
		((TaskDB) beanDB).addSubTask((Subtask)parms.getArgs());
		touch(sessionId);
	    } else if (cmd.equals("deleteProject")) {
		((TaskDB) beanDB).deleteProject(((Integer)parms.getArgs()).intValue());
		touch(sessionId);
	    } else if (cmd.equals("deleteSubTask")) {
		((TaskDB) beanDB).deleteSubTask(((Integer)parms.getArgs()).intValue());
		touch(sessionId);
	    } else if (cmd.equals("getLogs")) {
		result = ((TaskDB) beanDB).getLogs();
	    } else if (cmd.equals("getLogsI")) {
		result = ((TaskDB) beanDB).getLogs(((Integer)parms.getArgs()).intValue());
	    } else if (cmd.equals("getProject")) {
		result = ((TaskDB) beanDB).getProject(((Integer)parms.getArgs()).intValue());
	    } else if (cmd.equals("getProjects")) {
		result = ((TaskDB) beanDB).getProjects();
	    } else if (cmd.equals("getSubTasksI")) {
		result = ((TaskDB) beanDB).getSubTasks(((Integer)parms.getArgs()).intValue());
	    } else if (cmd.equals("getSubTasks")) {
		result = ((TaskDB) beanDB).getSubTasks();
	    } else if (cmd.equals("getTasks")) {
		result = ((TaskDB) beanDB).getTasks(((Integer)parms.getArgs()).intValue());
	    } else if (cmd.equals("nextProjectKey")) {
		result = new Integer(((TaskDB) beanDB).nextProjectKey());
	    } else if (cmd.equals("nextSubTaskKey")) {
		result = new Integer(((TaskDB) beanDB).nextSubTaskKey());
	    } else if (cmd.equals("saveLog")) {
		((TaskDB) beanDB).saveLog((Tasklog)parms.getArgs());
		touch(sessionId);
	    } else if (cmd.equals("updateProject")) {
		((TaskDB) beanDB).updateProject((Project)parms.getArgs());
		touch(sessionId);
	    } else if (cmd.equals("updateSubTask")) {
		((TaskDB) beanDB).updateSubTask((Subtask)parms.getArgs());
		touch(sessionId);
	    } else
		throw new UnsupportedOperationException(cmd);
	} catch (Exception e) {
	    e.printStackTrace();
	    if (e instanceof DBException)
		result = e;
	    else
		result = new DBException(e.getClass().getName() + ": "
			+ e.getLocalizedMessage());
	}

	String resultString = XmlObjectHelper.toXml(result).toString();
	// System.out.println("[OUTPUT] "+resultString);
	return resultString;
    }

    // private //
    private Map beanDBMap = new HashMap();

    private String url;

    private String lastUpdaterId = null;

    private Map dirtyQuerierMap = new HashMap();

    // who all has asked me if I'm dirty since the
    // last time someone dirtied me?

    private static String createClassKey(IRemoteProxy.Parms parms, String key) {
	return parms.getMyClass().getName() + "_" + key;
    }

    private BeanDB getBeanDB(IRemoteProxy.Parms parms, String username)
	    throws Exception {
	String key = createClassKey(parms, username);
	// ??? bounce logged off users from the map

	BeanDB beanDB = (BeanDB) beanDBMap.get(key);
	if (beanDB == null) {
	    String db = url;
	    StringBuffer tmp = new StringBuffer(db);
	    IBeanDataFactory factory = BeanDataFactoryFactory.getInstance()
		    .getFactory(tmp, true);
	    db = tmp.toString();
	    // allow the factory to tweak the URL

	    boolean console = Errmsg.console();
	    try {
		Errmsg.console(true);
		beanDB = factory.create(parms.getMyClass(), db, username);
		beanDBMap.put(key, beanDB);
	    } finally {
		Errmsg.console(console);
	    }
	}
	return beanDB;
    }

    private void touch(String sessionId) {
	lastUpdaterId = sessionId;
	dirtyQuerierMap.clear();
    }

    private boolean isDirty(IRemoteProxy.Parms parms, String sessionId) {
	if (lastUpdaterId == null)
	    return false;

	if (sessionId.equals(lastUpdaterId))
	    return false;
	// You were the one that last soiled me, so I'm not dirty to you.

	String key = createClassKey(parms, sessionId);
	if (dirtyQuerierMap.containsKey(key))
	    return false;
	// Nothing new has been dirtied since the last time you asked.

	dirtyQuerierMap.put(key, sessionId);
	// doesn't matter what the value is.

	return true;
    }
}