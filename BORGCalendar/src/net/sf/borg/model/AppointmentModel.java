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
 
 Copyright 2003 by Mike Berger
 */

package net.sf.borg.model;

import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.Vector;

import net.sf.borg.common.Errmsg;
import net.sf.borg.common.PrefName;
import net.sf.borg.common.Prefs;
import net.sf.borg.common.Resource;
import net.sf.borg.common.Warning;
import net.sf.borg.common.XTree;
import net.sf.borg.model.beans.Appointment;
import net.sf.borg.model.beans.AppointmentXMLAdapter;
import net.sf.borg.model.db.AppointmentDB;
import net.sf.borg.model.db.BeanDB;
import net.sf.borg.model.db.BeanDataFactoryFactory;
import net.sf.borg.model.db.DBException;
import net.sf.borg.model.db.IBeanDataFactory;
import net.sf.borg.model.db.MultiUserDB;

public class AppointmentModel extends Model implements Model.Listener {

    static private AppointmentModel default_reference_ = null;

    static private HashMap refs_ = new HashMap();

    public static AppointmentModel getReference() {
	return (default_reference_);
    }

    public static AppointmentModel getReference(String refName) {
	return ((AppointmentModel) refs_.get(refName));
    }

    public static AppointmentModel create() {
	default_reference_ = new AppointmentModel();
	return (default_reference_);
    }

    public static AppointmentModel create(String refName) {
	AppointmentModel am = new AppointmentModel();
	refs_.put(refName, am);
	return (am);
    }

    private BeanDB db_; // the SMDB database - see mdb.SMDB

    public BeanDB getDB() {
	return (db_);
    }

    /*
         * map_ contains each "base" day key that has appts and maps it to a
         * list of appt keys for that day.
         */
    private HashMap map_;

    private AppointmentModel() {
	map_ = new HashMap();
    }

    public void remove() {
	removeListeners();
	try {
	    if (db_ != null)
		db_.close();
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	    return;
	}
	db_ = null;
    }

    /**
         * return a base DB key for a given day
         */
    public static int dkey(int year, int month, int date) {
	return ((year - 1900) * 1000000 + (month + 1) * 10000 + date * 100);
    }

    public static int dkey(Calendar cal) {
	return dkey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal
		.get(Calendar.DATE));
    }

    public static Date dateFromKey(int dkey) {
	Calendar cal = new GregorianCalendar();
	int yr = (dkey / 1000000) % 1000 + 1900;
	int mo = (dkey / 10000) % 100 - 1;
	int day = (dkey / 100) % 100;
	cal.set(yr, mo, day, 0, 0, 0);
	return cal.getTime();
    }

    // return a key that only considers month and date
    public static int birthdayKey(int dkey) {
	return ((dkey % 1000000) * 1000000);
    }

    // get a new row from SMDB. The row will internally contain the Appt
    // schema
    public Appointment newAppt() {
	Appointment appt = (Appointment) db_.newObj();
	return (appt);
    }

    public void delAppt(int key) {
	try {
	    Appointment appt = getAppt(key);
	    if( appt != null )
		delAppt(appt);
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	}
    }

    // delete a row from the database
    public void delAppt(Appointment appt) {
	try {
	    String sync = Prefs.getPref(PrefName.PALM_SYNC);
	    if (sync.equals("true")) {
		appt.setDeleted(true);
		db_.updateObj(appt, false);
	    } else {
		db_.delete(appt.getKey());
	    }
	} catch (java.lang.NoClassDefFoundError ncf) {
	    // cannot find Prefs during sync
	    try {
		db_.delete(appt.getKey());
	    } catch (Exception e) {
		Errmsg.errmsg(e);
	    }

	} catch (Exception e) {
	    Errmsg.errmsg(e);
	}

	// even if delete fails - still refresh cache info
	// and tell listeners - db failure may have been due to
	// a sync causing a record already deleted error
	// this needs to be reflected in the map
	try {
	    // recreate the appointment hashmap
	    buildMap();

	    // refresh all views that are displaying appt data from this
	    // model
	    refreshListeners();
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	    return;
	}
    }

    // delete a row from the database
    public void forceDelete(Appointment appt) {
	try {
	    db_.delete(appt.getKey());
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	}

	// even if delete fails - still refresh cache info
	// and tell listeners - db failure may have been due to
	// a sync causing a record already deleted error
	// this needs to be reflected in the map
	try {
	    // recreate the appointment hashmap
	    buildMap();

	    // refresh all views that are displaying appt data from this
	    // model
	    refreshListeners();
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	    return;
	}
    }

    // delete one occurrence of a repeating appt
    public void delOneOnly(int key, int rkey) {
	try {

	    // read the appt row from SMDB
	    Appointment appt = (Appointment) db_.readObj(key);

	    // get the number of repeats
	    Integer tms = appt.getTimes();
	    if (tms == null)
		throw new Warning(Resource
			.getResourceString("Appointment_does_not_repeat"));

	    // get the list of repeats that have been deleted - the SKip
	    // list
	    Vector vect = appt.getSkipList();
	    if (vect == null)
		vect = new Vector();

	    // add the current appt key to the SKip list
	    vect.add(Integer.toString(rkey));
	    appt.setSkipList(vect);

	    // save the appt in SMDB
	    saveAppt(appt, false);
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	    return;
	}
    }

    // add a bulk list of appts and update the GUI afterwards
    public void bulkAdd(Collection apptList) throws Exception {
	// add list of appts
	Iterator it = apptList.iterator();
	while (it.hasNext()) {
	    saveAppt((Appointment) it.next(), true, true, false);
	}

	// refresh GUI
	try {
	    // recreate the appointment hashmap
	    buildMap();

	    // refresh all views that are displaying appt data from this
	    // model
	    refreshListeners();
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	    return;
	}
    }

    // called only from palm sync conduit
    public int syncSave(Appointment r) {
	if (r.getKey() == -1)
	    saveAppt(r, true, false, true);
	else
	    saveAppt(r, false, false, true);

	return (r.getKey());

    }

    public void saveAppt(Appointment r, boolean add) {
	saveAppt(r, add, false, false);
    }

    // save an appt in SMDB
    // if bulk - don't update listeners
    public void saveAppt(Appointment r, boolean add, boolean bulk, boolean sync) {

	try {

	    // check is the appt is private and set encrypt flag if it is
	    boolean crypt = r.getPrivate();

	    if (add == true) {
		// get the next unused key for a given day
		// to do this, start with the "base" key for a given day.
		// then see if an appt has this key.
		// keep adding 1 until a key is found that has no appt
		GregorianCalendar gcal = new GregorianCalendar();
		gcal.setTime(r.getDate());
		int key = AppointmentModel.dkey(gcal);

		try {
		    while (true) {
			Appointment ap = getAppt(key);
			if (ap == null)
			    break;
			key++;
		    }
		} catch (DBException e) {
		    if (e.getRetCode() != DBException.RET_NOT_FOUND) {
			throw e;
		    }
		} catch (Exception ee) {
		    Errmsg.errmsg(ee);
		    return;
		}

		// key is now a free key
		r.setKey(key);
		if (!sync) {
		    r.setNew(true);
		    r.setDeleted(false);
		}

		db_.addObj(r, crypt);
	    } else {
		if (!sync) {
		    r.setModified(true);
		    r.setDeleted(false);
		}
		db_.updateObj(r, crypt);
	    }

	    // update category list
	    String cat = r.getCategory();
	    if (cat != null && !cat.equals(""))
		CategoryModel.getReference().addCategory(cat);

	} catch (Exception e) {
	    Errmsg.errmsg(e);

	}

	if (!bulk) {
	    try {
		// recreate the appointment hashmap
		buildMap();

		// refresh all views that are displaying appt data from this
		// model
		refreshListeners();
	    } catch (Exception e) {
		Errmsg.errmsg(e);
		return;
	    }
	}

    }

    // get an appt from the database by key
    public Appointment getAppt(int key) throws DBException, Exception {
	Appointment appt = (Appointment) db_.readObj(key);
	return (appt);
    }

    // search the appt DB using a search string and
    // create a Vector containing the results
    public Vector get_srch(String s, boolean case_sensitive) {

	Vector res = new Vector();
	String uncat = CategoryModel.UNCATEGORIZED;
	try {

	    // load all appts into appt list
	    Iterator itr = getAllAppts().iterator();
	    while (itr.hasNext()) {
		// read each appt
		Appointment appt = (Appointment) itr.next();

		// if category set, filter appts

		String cat = appt.getCategory();

		if (cat == null || cat.equals(""))
		    cat = uncat;

		if (!CategoryModel.getReference().isShown(cat)) {
		    continue;
		}

		String tx = appt.getText();
		Date d = appt.getDate();
		if (d == null || tx == null)
		    continue;

		if (case_sensitive) {
		    // check if appt text contains the search string
		    if (tx.indexOf(s) == -1)
			continue;
		} else {
		    // check if appt text contains the search string
		    String ltx = tx.toLowerCase();
		    String ls = s.toLowerCase();
		    if (ltx.indexOf(ls) == -1)
			continue;
		}

		// add the appt to the search results
		res.add(appt);

	    }
	} catch (DBException e) {
	    if (e.getRetCode() != DBException.RET_NOT_FOUND)
		Errmsg.errmsg(e);
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	}
	return (res);

    }

    // this function is called to mark a to do as done from the todo gui
    // window
    // the user can optionally indicate that the todo is to be deleted - but
    // we must still
    // make sure it is the last repeat if the todo repeats
    public void do_todo(int key, boolean del) throws Exception {
	// read the DB row for the ToDo
	Appointment appt = (Appointment) db_.readObj(key);

	// curtodo is the date of the todo that is to be "done"
	Date curtodo = appt.getNextTodo();
	Date d = appt.getDate();
	if (curtodo == null) {
	    curtodo = d;
	}

	// System.out.println("curtodo=" + curtodo);

	// newtodo will be the name of the next todo occurrence (if the todo
	// repeats and is not done)
	Date newtodo = null;

	Integer tms = appt.getTimes();
	String rpt = Repeat.getFreq(appt.getFrequency());

	// find next to do if it repeats by doing calendar math
	if (tms != null && tms.intValue() > 1 && rpt != null
		&& !rpt.equals(Repeat.ONCE)) {
	    int tm = tms.intValue();

	    Calendar ccal = new GregorianCalendar();
	    Calendar ncal = new GregorianCalendar();

	    // ccal is the current todo and ncal is the original appt date
	    // ncal will be incremented until we find the todo after the
	    // one in ccal
	    ccal.setTime(curtodo);
	    ncal.setTime(d);

	    Repeat repeat = new Repeat(ncal, appt.getFrequency());
	    for (int i = 1; i < tm; i++) {
		//System.out.println("i="+i);
		//System.out.println("ncal=" + ncal.getTime());

		// if we have gotten to the current todo,
		// signal that we're ready to break out of
		// the loop as soon as we find a suitable ToDo date.
		if (ncal != null
			&& ncal.get(Calendar.YEAR) == ccal.get(Calendar.YEAR)
			&& ncal.get(Calendar.MONTH) == ccal.get(Calendar.MONTH)
			&& ncal.get(Calendar.DATE) == ccal.get(Calendar.DATE))
		{
		    ncal = repeat.next();
		    if( ncal != null )
		    {
			newtodo = ncal.getTime();
		    }
		    //System.out.println("newtodo=" + newtodo.getTime());
		    break;
		}
		    
		ncal = repeat.next();

	    }
	}

	if (newtodo != null) {
	    // a next todo was found, set NT to that value
	    // and don't delete the appt
	    appt.setNextTodo(newtodo);
	    saveAppt(appt, false);
	} else {
	    // there is no next todo - shut off the todo
	    // unless the user wants it deleted. if so, delete it.
	    if (del) {
		delAppt(appt);
	    } else {
		appt.setTodo(false);
		appt.setColor("strike");
		saveAppt(appt, false);
	    }
	}

    }

    // get a list of appts for a given day key
    public List getAppts(int key) {
	return ((List) map_.get(new Integer(key)));
    }

    // get a vector containing all of the todo appts in the DB
    public Vector get_todos() {

	String uncat = CategoryModel.UNCATEGORIZED;
	Vector av = new Vector();
	try {

	    // iterate through appts in the DB
	    AppointmentDB kf = (AppointmentDB) db_;
	    Collection keycol = kf.getTodoKeys();
	    // Collection keycol = AppointmentHelper.getTodoKeys(db_);
	    Iterator keyiter = keycol.iterator();
	    while (keyiter.hasNext()) {
		Integer ki = (Integer) keyiter.next();
		int key = ki.intValue();

		// read the full appt from the DB and add to the vector
		Appointment appt = (Appointment) db_.readObj(key);
		if (appt.getDeleted())
		    continue;

		// if category set, filter appts

		String cat = appt.getCategory();
		if (cat == null || cat.equals(""))
		    cat = uncat;
		if (!CategoryModel.getReference().isShown(cat)) {
		    continue;
		}

		av.add(appt);
	    }
	} catch (DBException e) {
	    if (e.getRetCode() != DBException.RET_NOT_FOUND)
		Errmsg.errmsg(e);
	} catch (Exception ee) {
	    Errmsg.errmsg(ee);
	}

	return (av);

    }

    // return true if there are any todos
    public boolean haveTodos() {
	try {
	    AppointmentDB kf = (AppointmentDB) db_;
	    Collection keycol = kf.getTodoKeys();
	    // Collection keycol = AppointmentHelper.getTodoKeys(db_);
	    if (keycol.size() != 0)
		return (true);
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	}

	return (false);
    }

    // open the SMDB database
    public void open_db(String url, String username, 
	    boolean shared) throws Exception {

	StringBuffer tmp = new StringBuffer(url);
	IBeanDataFactory factory = BeanDataFactoryFactory.getInstance()
		.getFactory(tmp, shared);
	url = tmp.toString();
	// let the factory tweak dbdir

	db_ = factory.create(Appointment.class, url, username);

	// init categories and currentcategories
	CategoryModel.getReference().addAll(getDbCategories());
	CategoryModel.getReference().addListener(this);
	MultiUserModel.getReference().addListener(this);

	try {
	    // scan the DB and build the appt map_
	    buildMap();
	} catch (DBException e) {
	    if (e.getRetCode() != DBException.RET_NOT_FOUND)
		throw e;
	}

    }

    public Collection getDbCategories() throws Exception, DBException {

	TreeSet dbcat = new TreeSet();
	dbcat.add(CategoryModel.UNCATEGORIZED);
	Iterator itr = getAllAppts().iterator();
	while (itr.hasNext()) {
	    Appointment ap = (Appointment) itr.next();
	    String cat = ap.getCategory();
	    if (cat != null && !cat.equals(""))
		dbcat.add(cat);
	}

	return (dbcat);

    }

    // the calmodel keeps a hashmap of days to appt keys to avoid hitting
    // the DB when possible - although the DB is cached too to some extent
    // buildmap will rebuild the map based on the DB
    private void buildMap() throws Exception {
	// erase the current map
	map_.clear();
	String uncat = CategoryModel.UNCATEGORIZED;

	// get the year for later
	GregorianCalendar cal = new GregorianCalendar();
	int curyr = cal.get(Calendar.YEAR);

	try {
	    // scan entire DB
	    Iterator itr = getAllAppts().iterator();
	    AppointmentDB kf = (AppointmentDB) db_;
	    Collection rptkeys = kf.getRepeatKeys();

	    while (itr.hasNext()) {
		Appointment appt = (Appointment) itr.next();
		String cat = appt.getCategory();
		if (cat == null || cat.equals(""))
		    cat = uncat;
		if (!CategoryModel.getReference().isShown(cat)) {
		    continue;
		}

		// if appt does not repeat, we can add its
		// key to a single day
		int key = appt.getKey();
		Integer ki = new Integer(key);
		if (!rptkeys.contains(ki)) {
		    // strip of appt number
		    int dkey = (key / 100) * 100;

		    // get/add entry for the day in the map
		    Object o = map_.get(new Integer(dkey));
		    if (o == null) {
			o = new LinkedList();
			map_.put(new Integer(dkey), o);
		    }

		    // add the appt key to the day's list
		    LinkedList l = (LinkedList) o;
		    l.add(new Integer(key));
		} else {
		    // appt repeats so we have to add all of the repeats
		    // into the map (well maybe not all)
		    int yr = (key / 1000000) % 1000 + 1900;
		    int mo = (key / 10000) % 100 - 1;
		    int day = (key / 100) % 100;
		    cal.set(yr, mo, day);

		    Repeat repeat = new Repeat(cal, appt.getFrequency());
		    if (!repeat.isRepeating())
			continue;
		    Integer times = appt.getTimes();
		    if (times == null)
			times = new Integer(1);
		    int tm = times.intValue();

		    // get skip list
		    Vector skv = appt.getSkipList();

		    // ok, plod through the repeats now
		    for (int i = 0; i < tm; i++) {
			Calendar current = repeat.current();
			if (current == null) {
			    repeat.next();
			    continue;
			}

			// get the day key for the repeat
			int rkey = dkey(current);

			int cyear = current.get(Calendar.YEAR);

			// limit the repeats to 2 years
			// from the current year
			// otherwise, an appt repeating 9999 times
			// could kill BORG
			if (cyear > curyr + 2)
			    break;

			// check if the repeat is in the skip list
			// if so, skip it
			String srk = Integer.toString(rkey);
			if (skv == null || !skv.contains(srk)) {
			    // add the repeat key to the map
			    Object o = map_.get(new Integer(rkey));
			    if (o == null) {
				o = new LinkedList();
				map_.put(new Integer(rkey), o);
			    }
			    LinkedList l = (LinkedList) o;
			    l.add(new Integer(key));
			}

			repeat.next();
		    }
		}
	    }
	} catch (DBException e) {
	    if (e.getRetCode() != DBException.RET_NOT_FOUND)
		throw e;
	}

    }

    // export the appt data to a file in XML
    public void export(Writer fw) throws Exception {

	// FileWriter fw = new FileWriter(fname);
	fw.write("<APPTS>\n");
	AppointmentXMLAdapter aa = new AppointmentXMLAdapter();

	// export options
	try {
	    Collection opts = db_.getOptions();
	    Iterator opiter = opts.iterator();
	    while (opiter.hasNext()) {
		BorgOption option = (BorgOption) opiter.next();
		XTree xt = new XTree();
		xt.name("OPTION");
		xt.appendChild(option.getKey(), option.getValue());
		fw.write(xt.toString());
	    }
	} catch (DBException e) {
	    if (e.getRetCode() != DBException.RET_NOT_FOUND)
		Errmsg.errmsg(e);
	}

	// export appts
	try {
	    Iterator itr = getAllAppts().iterator();
	    while (itr.hasNext()) {
		Appointment ap = (Appointment) itr.next();
		XTree xt = aa.toXml(ap);
		fw.write(xt.toString());
	    }
	} catch (DBException e) {
	    if (e.getRetCode() != DBException.RET_NOT_FOUND)
		Errmsg.errmsg(e);
	}

	fw.write("</APPTS>");

    }

    // export the appt data to a file in XML
    public void importXml(XTree xt) throws Exception {

	AppointmentXMLAdapter aa = new AppointmentXMLAdapter();

	// for each appt - create an Appointment and store
	for (int i = 1;; i++) {
	    XTree ch = xt.child(i);
	    if (ch == null)
		break;

	    if (ch.name().equals("OPTION")) {
		XTree opt = ch.child(1);
		if (opt == null)
		    continue;

		// do not import categories
		// do a sync instead
		if (opt.name().equals("categories"))
		    continue;
		db_.setOption(new BorgOption(opt.name(), opt.value()));
	    }

	    if (!ch.name().equals("Appointment"))
		continue;
	    Appointment appt = (Appointment) aa.fromXml(ch);
	    boolean crypt = appt.getPrivate();

	    // create new key if none exists
	    if (appt.getKey() == 0) {
		Date d = appt.getDate();
		GregorianCalendar gcal = new GregorianCalendar();
		gcal.setTime(d);
		int key = AppointmentModel.dkey(gcal);
		appt.setKey(key);
	    }

	    while (true) {
		try {
		    db_.addObj(appt, crypt);
		    break;
		} catch (DBException me) {
		    if (me.getRetCode() == DBException.RET_DUPLICATE) {
			int k = appt.getKey();
			appt.setKey(k + 1);
		    } else {
			Errmsg.errmsg(me);
			break;
		    }
		} catch (Exception e) {
		    Errmsg.errmsg(e);
		    break;
		}
	    }
	}

	// rebuild the hashmap
	buildMap();

	CategoryModel.getReference().syncCategories();

	// refresh all views that are displaying appt data from this model
	refreshListeners();

    }

    public static boolean isNote(Appointment appt) {
	// return true if the appt Appointment represents a "note" or
	// "non-timed" appt
	// this is true if the time is midnight and duration is 0.
	// this method was used for backward compatibility - as opposed to
	// adding
	// a new flag to the DB
	try {
	    Integer duration = appt.getDuration();
	    if (duration != null && duration.intValue() != 0)
		return (false);

	    Date d = appt.getDate();
	    if (d == null)
		return (true);

	    GregorianCalendar g = new GregorianCalendar();
	    g.setTime(d);
	    int hour = g.get(Calendar.HOUR_OF_DAY);
	    if (hour != 0)
		return (false);

	    int min = g.get(Calendar.MINUTE);
	    if (min != 0)
		return (false);
	} catch (Exception e) {
	    return (true);
	}

	return (true);

    }

    public static SimpleDateFormat getTimeFormat() {
	String mt = Prefs.getPref(PrefName.MILTIME);
	if (mt.equals("true")) {
	    return (new SimpleDateFormat("HH:mm"));
	}

	return (new SimpleDateFormat("h:mm a"));

    }

    public void sync() throws DBException {
	db_.sync();
	try {
	    // recreate the appointment hashmap
	    buildMap();

	    // refresh all views that are displaying appt data from this
	    // model
	    refreshListeners();
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	    return;
	}
    }

    public void close_db() throws Exception {
	db_.close();
    }

    public Collection getAllAppts() throws Exception {
	Collection appts = db_.readAll();
	Iterator it = appts.iterator();
	while (it.hasNext()) {
	    Appointment appt = (Appointment) it.next();
	    if (appt.getDeleted())
		it.remove();
	}
	return appts;
    }

    public Collection getDeletedAppts() throws Exception {
	Collection appts = db_.readAll();
	Iterator it = appts.iterator();
	while (it.hasNext()) {
	    Appointment appt = (Appointment) it.next();
	    if (!appt.getDeleted())
		it.remove();
	}
	return appts;
    }

    /*
         * (non-Javadoc)
         * 
         * @see net.sf.borg.model.Model.Listener#refresh()
         */
    public void refresh() {

	try {
	    buildMap();
	} catch (Exception e) {
	    Errmsg.errmsg(e);
	}

	// refresh all views that are displaying appt data from this model
	refreshListeners();

    }

    public Collection getAllUsers() throws Exception {
	// System.out.println( db_.toString());
	if (db_ instanceof MultiUserDB) {
	    return ((MultiUserDB) db_).getAllUsers();
	}

	return null;
    }

    public void setPublic(boolean b) throws Exception {
	if (b) {
	    db_.setOption(new BorgOption("PUBLIC", "true"));
	} else {
	    db_.setOption(new BorgOption("PUBLIC", ""));
	}
    }

    public boolean isPublic() throws Exception {
	String s = db_.getOption("PUBLIC");
	if (s == null || !s.equals("true")) {
	    return false;
	}

	return true;
    }

}