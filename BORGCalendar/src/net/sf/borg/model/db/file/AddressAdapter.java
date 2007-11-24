// This code was generated by GenerateDataObjects
package net.sf.borg.model.db.file;

import net.sf.borg.model.beans.Address;
import net.sf.borg.model.beans.KeyedBean;
import net.sf.borg.model.db.file.mdb.Row;
import net.sf.borg.model.db.file.mdb.Schema;
public class AddressAdapter implements FileBeanAdapter {

	public KeyedBean fromRow( Row r ) throws Exception
	{
		Address ret = new Address();
		ret.setKey(r.getKey());
		ret.setFirstName( r.getString("FN"));
		ret.setLastName( r.getString("LN"));
		ret.setNickname( r.getString("NN"));
		ret.setEmail( r.getString("EM"));
		ret.setScreenName( r.getString("SN"));
		ret.setWorkPhone( r.getString("WP"));
		ret.setHomePhone( r.getString("HP"));
		ret.setFax( r.getString("FX"));
		ret.setPager( r.getString("PG"));
		ret.setStreetAddress( r.getString("SA"));
		ret.setCity( r.getString("CT"));
		ret.setState( r.getString("ST"));
		ret.setZip( r.getString("ZC"));
		ret.setCountry( r.getString("CN"));
		ret.setCompany( r.getString("CMP"));
		ret.setWorkStreetAddress( r.getString("WSA"));
		ret.setWorkCity( r.getString("WCT"));
		ret.setWorkState( r.getString("WST"));
		ret.setWorkZip( r.getString("WZC"));
		ret.setWorkCountry( r.getString("WCN"));
		ret.setWebPage( r.getString("WB"));
		ret.setNotes( r.getString("NO"));
		ret.setBirthday( r.getDate("BD"));
		ret.setNew( r.getFlag(1));
		ret.setModified( r.getFlag(2));
		ret.setDeleted( r.getFlag(3));
		return( ret );
	}

	public Row toRow( Schema sch, KeyedBean b, boolean normalize ) throws Exception
	{
		Address o = (Address) b;
		Row ret = new Row(sch);
		ret.normalize(normalize);
		ret.setKey(o.getKey());
		ret.setField("FN", o.getFirstName());
		ret.setField("LN", o.getLastName());
		ret.setField("NN", o.getNickname());
		ret.setField("EM", o.getEmail());
		ret.setField("SN", o.getScreenName());
		ret.setField("WP", o.getWorkPhone());
		ret.setField("HP", o.getHomePhone());
		ret.setField("FX", o.getFax());
		ret.setField("PG", o.getPager());
		ret.setField("SA", o.getStreetAddress());
		ret.setField("CT", o.getCity());
		ret.setField("ST", o.getState());
		ret.setField("ZC", o.getZip());
		ret.setField("CN", o.getCountry());
		ret.setField("CMP", o.getCompany());
		ret.setField("WSA", o.getWorkStreetAddress());
		ret.setField("WCT", o.getWorkCity());
		ret.setField("WST", o.getWorkState());
		ret.setField("WZC", o.getWorkZip());
		ret.setField("WCN", o.getWorkCountry());
		ret.setField("WB", o.getWebPage());
		ret.setField("NO", o.getNotes());
		ret.setField("BD", o.getBirthday());
		ret.setFlag(1,  o.getNew());
		ret.setFlag(2,  o.getModified());
		ret.setFlag(3,  o.getDeleted());
		return( ret );
	}

	public KeyedBean newBean() {
		return( new Address());
	}
}