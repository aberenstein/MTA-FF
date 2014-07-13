package psdi.app.invoice;

import java.rmi.RemoteException;

import psdi.app.po.FldPONum;
import psdi.mbo.MboRemote;
import psdi.mbo.MboSetRemote;
import psdi.mbo.MboValue;
import psdi.mbo.SqlFormat;
import psdi.util.MXApplicationException;
import psdi.util.MXException;

public class FldInvoiceLinePONum extends FldPONum
{
	public FldInvoiceLinePONum(MboValue mbv) throws MXException, RemoteException
	{
		super(mbv);

		setLookupKeyMapInOrder(new String[] { "positeid", "porevisionnum", "ponum" }, new String[] { "siteid", "revisionnum", "ponum" });
	}
  
	public void init() throws MXException, RemoteException
	{
		MboRemote invoice = getMboValue().getMbo().getOwner();
		if ((invoice instanceof InvoiceTransRemote)) {
			return;
		}
		if (!invoice.isNull("ponum")) {
			getMboValue().setFlag(7L, true);
		}
	}
  
	public void validate() throws MXException, RemoteException
	{
		if (getMboValue().isNull()) {
			return;
		}
		
		MboRemote invoiceMbo = getMboValue().getMbo().getOwner();
    
		String type = invoiceMbo.getString("documenttype");
		if (getTranslator().toInternalString("INVTYPE", type, invoiceMbo).equalsIgnoreCase("CONSIGNMENT")) {
			throw new MXApplicationException("invoice", "cannotusepo");
		}
		
		if (getMboValue("positeid").isNull())
		{
			Object[] params = { getMboValue().getString(), Integer.valueOf(getMboValue("polinenum").getInt()), getMboValue("invoicenum").getString(), Integer.valueOf(getMboValue("invoicelinenum").getInt()) };
			throw new MXApplicationException("invoice", "InvoiceLinePOSiteIDRequired", params);
		}
		
		if (invoiceMbo.isNull("ponum"))
		{
			String ponum = getMboValue().getString();
			String poAppr = getTranslator().toExternalList("POSTATUS", "APPR", invoiceMbo);
			String poInprg = getTranslator().toExternalList("POSTATUS", "INPRG", invoiceMbo);
			String poClose = getTranslator().toExternalList("POSTATUS", "CLOSE", invoiceMbo);
			String statuses = poAppr + ", " + poInprg + ", " + poClose;
			if (!getMboValue().getMbo().getUserInfo().isInteractive()) {
				statuses = statuses + ", " + getTranslator().toExternalList("POSTATUS", "REVISE", invoiceMbo);
			}
			String query = "status in (" + statuses + ") and ponum = :1 and siteid = :2";
      
			SqlFormat sqfNew = new SqlFormat(invoiceMbo.getUserInfo(), query);
			sqfNew.setObject(1, "INVOICELINE", "ponum", ponum);
			sqfNew.setObject(2, "INVOICELINE", "positeid", getMboValue("positeid").getString());
      
			MboSetRemote poSet = getMboValue().getMbo().getMboSet("$invoicelinepo", "PO", sqfNew.format());
			MboRemote po = null;
			if (poSet.isEmpty()) {
				throw new MXApplicationException("po", "InvalidPONum");
			}
			po = poSet.getMbo(0);
      
			String invoiceOrgId = getMboValue("orgid").getString();
			String poOrgId = po.getString("orgid");
			if (!invoiceOrgId.equals(poOrgId))
			{
				getMboValue("positeid").setValueNull(2L);
				Object[] params = { getMboValue("ORGID").getColumnTitle(), invoiceOrgId };
				throw new MXApplicationException("system", "OrgIdCannotBeSet", params);
			}
      
			if (po.getBoolean("internal")) {
				throw new MXApplicationException("po", "InvalidPONum");
			}
			String poStatus = po.getString("status");
      
			getMboValue("porevisionnum").setValue(po.getInt("revisionnum"), 11L);
			if ((getTranslator().toInternalString("POSTATUS", poStatus, invoiceMbo).equalsIgnoreCase("CAN")) || (getTranslator().toInternalString("POSTATUS", poStatus, invoiceMbo).equalsIgnoreCase("WAPPR")))
			{
				poSet.reset();
				Object[] params = { ponum, poAppr, poInprg, poClose };
				throw new MXApplicationException("invoice", "InvalidPOStatus", params);
			}
			
			String invoiceVendor = invoiceMbo.getString("vendor");
			String poVendor = po.getString("vendor");
			if ((invoiceVendor != null) && (!invoiceVendor.equals("")))
			{
				query = "status in (" + statuses + ") and siteid = :4";
				query = query + " and :1 in ((select company from companies where company = :2 and orgid = :3) union (select company from companies where parentcompany = :2 and orgid = :3))";
        
				SqlFormat sqf = new SqlFormat(invoiceMbo.getUserInfo(), query);
				sqf.setObject(1, "PO", "vendor", poVendor);
				sqf.setObject(2, "INVOICE", "vendor", invoiceVendor);
				sqf.setObject(3, "INVOICE", "orgid", invoiceMbo.getString("orgid"));
		        sqf.setObject(4, "INVOICE", "positeid", getMboValue("positeid").getString());
		        if (((Invoice)invoiceMbo).getMboSet("$potoinvoice", "PO", sqf.format()).isEmpty())
		        {
		        	poSet.reset();
		        	Object[] params = { ponum };
		        	throw new MXApplicationException("invoice", "WrongPOVendor", params);
		        }
			}
          
			/**********************************************************************************/
			/* Validación de la fecha de factura contra la fecha de autorización de la OC     */
			/**********************************************************************************/

				FldInvoiceInvoiceDate.validatePO((Invoice)invoiceMbo, po, false);

			/**********************************************************************************/
			/* Fin validación de la fecha de factura contra la fecha de autorización de la OC */
			/**********************************************************************************/
		}
	}
  
	public void action() throws MXException, RemoteException
	{
		if (getMboValue().isNull())
		{
			getMboValue("porevisionnum").setValueNull(2L);
			getMboValue("positeid").setValueNull(2L);
			return;
		}
		getMboValue("polinenum").setFlag(7L, false);
	}
  
	public boolean hasList()
	{
		return true;
	}
  
	public MboSetRemote getList() throws MXException, RemoteException
	{
		Invoice mbo = (Invoice)getMboValue().getMbo().getOwner();
		if (mbo.getMboValue("ponum").isNull())
		{
			String wappr = getTranslator().toExternalList("POSTATUS", "WAPPR", mbo);
			String cancel = getTranslator().toExternalList("POSTATUS", "CAN", mbo);
			String revise = getTranslator().toExternalList("POSTATUS", "REVISE", mbo);
			String pndrev = getTranslator().toExternalList("POSTATUS", "PNDREV", mbo);
			String hold = getTranslator().toExternalList("POSTATUS", "HOLD", mbo);
			String consingment = getTranslator().toExternalList("POTYPE", "CONSIGNMENT", mbo);

			String query = "potype not in (" + consingment + ") and status not in (" + wappr + "," + cancel + "," + revise + "," + pndrev + "," + hold + ")  and internal = :no and orgid = :3";
			if (!getMboValue("vendor").isNull())
			{
				String invoiceVendor = mbo.getString("vendor");
				query = query + " and vendor in ((select company from companies where company = :1 and orgid = :2) union (select company from companies where parentcompany = :1 and orgid = :2))";

				SqlFormat sqf = new SqlFormat(mbo.getUserInfo(), query);
				sqf.setObject(1, "INVOICE", "vendor", invoiceVendor);
				sqf.setObject(2, "INVOICE", "orgid", mbo.getString("orgid"));
				sqf.setObject(3, "INVOICE", "orgid", mbo.getString("orgid"));

				return mbo.getMboSet("$potoinvoicevendor", "PO", sqf.format());
			}
			
			SqlFormat sqf1 = new SqlFormat(mbo.getUserInfo(), query);
			sqf1.setObject(3, "INVOICE", "orgid", mbo.getString("orgid"));

			return mbo.getMboSet("$potoinvoicenovendor", "PO", sqf1.format());
		}
		
		return getMboValue().getMbo().getMboSet("PO");
	}
}
