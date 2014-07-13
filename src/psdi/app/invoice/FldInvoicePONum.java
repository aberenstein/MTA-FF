package psdi.app.invoice;

import java.rmi.RemoteException;
import psdi.app.common.TaxUtility;
import psdi.app.po.FldPONum;
import psdi.mbo.Mbo;
import psdi.mbo.MboRemote;
import psdi.mbo.MboSetRemote;
import psdi.mbo.MboValue;
import psdi.mbo.SqlFormat;
import psdi.util.MXApplicationException;
import psdi.util.MXException;

public class FldInvoicePONum extends FldPONum
{
	public FldInvoicePONum(MboValue mbv) throws MXException, RemoteException
	{
		super(mbv);
		setLookupKeyMapInOrder(new String[] { "positeid", "ponum" }, new String[] { "siteid", "ponum" });
	}

	public void validate() throws MXException, RemoteException
	{
		if (getMboValue().isNull()) {
			return;
		}
		
		try
		{
			super.validate();
		}
		catch (MXApplicationException ex)
		{
			if (ex.equals("po", "InvalidPONum")) {
				throw new MXApplicationException("inventory", "invalidpo");
			}
			throw ex;
		}
		
		Invoice invoiceMbo = (Invoice)getMboValue().getMbo();

		String type = getMboValue("documenttype").getString();
		if (getTranslator().toInternalString("INVTYPE", type, invoiceMbo).equalsIgnoreCase("CONSIGNMENT")) {
			throw new MXApplicationException("invoice", "cannotusepo");
		}
		
		String ponum = getMboValue().getString();
		if (invoiceMbo.isNull("positeid"))
		{
			Object[] params = { ponum, invoiceMbo.getString("invoicenum") };
			throw new MXApplicationException("invoice", "InvoicePOSiteIDRequired", params);
		}
		
		MboSetRemote poSet = invoiceMbo.getMboSet("PO");
		if (poSet.isEmpty()) {
			throw new MXApplicationException("po", "InvalidPONum");
		}

		MboRemote po = poSet.getMbo(0);
    
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
		String poAppr = getTranslator().toExternalList("POSTATUS", "APPR", invoiceMbo);
		String poInprg = getTranslator().toExternalList("POSTATUS", "INPRG", invoiceMbo);
		String poClose = getTranslator().toExternalList("POSTATUS", "CLOSE", invoiceMbo);
		if (!getTranslator().toInternalString("INVTYPE", invoiceMbo.getString("documenttype")).equals("SCHED")) {
			if ((getTranslator().toInternalString("POSTATUS", poStatus, invoiceMbo).equalsIgnoreCase("CAN")) || (getTranslator().toInternalString("POSTATUS", poStatus, invoiceMbo).equalsIgnoreCase("WAPPR")))
			{
				poSet.reset();
				Object[] params = { ponum, poAppr, poInprg, poClose };
				throw new MXApplicationException("invoice", "InvalidPOStatus", params);
			}
		}
		
		String invoiceVendor = invoiceMbo.getString("vendor");
		String poVendor = po.getString("vendor");
		if ((invoiceVendor != null) && (!invoiceVendor.equals("")))
		{
			String query = "status in (" + poAppr + "," + poInprg + "," + poClose + ")";

			query = query + " and :1 in ((select company from companies where company = :2 and orgid = :3) union (select company from companies where parentcompany = :2 and orgid = :3))";

			SqlFormat sqf = new SqlFormat(invoiceMbo.getUserInfo(), query);
			sqf.setObject(1, "PO", "vendor", poVendor);
			sqf.setObject(2, "INVOICE", "vendor", invoiceVendor);
			sqf.setObject(3, "INVOICE", "orgid", invoiceMbo.getString("orgid"));
			if (invoiceMbo.getMboSet("$potoinvoice", "PO", sqf.format()).isEmpty())
			{
				poSet.reset();
				Object[] params = { ponum };
				throw new MXApplicationException("invoice", "WrongPOVendor", params);
			}
		}
    
		/**********************************************************************************/
		/* Validación de la fecha de factura contra la fecha de autorización de la OC     */
		/**********************************************************************************/
    
		FldInvoiceInvoiceDate.validatePO(invoiceMbo, false);

		/**********************************************************************************/
		/* Fin validación de la fecha de factura contra la fecha de autorización de la OC */
		/**********************************************************************************/
	}
  
	public void action() throws MXException, RemoteException
	{
		Invoice invoice = (Invoice)getMboValue().getMbo();
    
		MboRemote company = null;
		if (getMboValue().isNull())
		{
			if (!getMboValue("vendor").isNull())
			{
				company = invoice.getMboSet("COMPANIES").getMbo(0);
        
				getMboValue("currencycode").setValue(company.getString("currencycode"), 11L);

				getMboValue().getMbo().setFieldFlag("vendor", 7L, false);
			}

			getMboValue("uninvoicedtotal").setValueNull(11L);
			invoice.setValueNull("contractrefnum", 2L);
			invoice.setFieldFlag("contractrefnum", 7L, false);
			invoice.setValueNull("positeid", 2L);

			return;
		}
		
		MboSetRemote invoicePOSet = invoice.getMboSet("PO");
		MboRemote invoicePO = invoicePOSet.getMbo(0);
		if (getMboValue("vendor").isNull()) {
			invoice.setValue("vendor", invoicePO.getString("vendor"), 2L);
		}
		invoice.setFieldFlag("vendor", 7L, true);
		invoice.setValue("paymentterms", invoicePO.getString("paymentterms"), 2L);
		invoice.setValue("currencycode", invoicePO.getString("currencycode"), 2L);
    
		TaxUtility taxUtility = TaxUtility.getTaxUtility();
		taxUtility.setTaxattrValue(invoice, "INCLUSIVE", invoicePO, 11L);
		invoice.setValue("contractrefnum", invoicePO.getString("contractrefnum"), 11L);
		invoice.setValue("contractrefid", invoicePO.getString("contractrefid"), 11L);
		invoice.setValue("contractrefrev", invoicePO.getString("contractrefrev"), 11L);
		invoice.setFieldFlag("contractrefnum", 7L, true);
		if (invoicePO.getBoolean("buyahead"))
		{
			invoice.setValue("exchangerate", invoicePO.getString("exchangerate"), 2L);
			invoice.setValue("exchangerate2", invoicePO.getString("exchangerate2"), 2L);
			invoice.setValue("exchangedate", invoicePO.getString("exchangedate"), 2L);
		}
		invoice.setValue("uninvoicedtotal", invoice.calculateUnInvoicedTotal(), 11L);
	}
  
	public boolean hasList()
	{
		return true;
	}
  
	public MboSetRemote getList() throws MXException, RemoteException
	{
		Mbo mbo = getMboValue().getMbo();

		String wappr = getTranslator().toExternalList("POSTATUS", "WAPPR", mbo);
		String cancel = getTranslator().toExternalList("POSTATUS", "CAN", mbo);
		String revise = getTranslator().toExternalList("POSTATUS", "REVISE", mbo);
		String pndrev = getTranslator().toExternalList("POSTATUS", "PNDREV", mbo);
		String hold = getTranslator().toExternalList("POSTATUS", "HOLD", mbo);
		String consingment = getTranslator().toExternalList("POTYPE", "CONSIGNMENT", mbo);

		String query = "potype not in (" + consingment + ") and status not in (" + wappr + "," + cancel + "," + revise + "," + pndrev + "," + hold + ") and internal = :no and orgid = :2";
		if (!getMboValue("vendor").isNull())
		{
			String invoiceVendor = mbo.getString("vendor");

			query = query + " and vendor in ((select company from companies where company = :1 and orgid = :2) union (select company from companies where parentcompany = :1 and orgid = :2))";
      
			SqlFormat sqf = new SqlFormat(mbo.getUserInfo(), query);
			sqf.setObject(1, "INVOICE", "vendor", invoiceVendor);
			if (!mbo.isZombie()) {
				sqf.setObject(2, "INVOICE", "orgid", mbo.getString("orgid"));
			} else {
				sqf.setObject(2, "INVOICE", "orgid", mbo.getProfile().getInsertOrg());
			}
			setListCriteria(sqf.format());
		}
		else
		{
			SqlFormat sqf = new SqlFormat(mbo.getUserInfo(), query);
			if (!mbo.isZombie()) {
				sqf.setObject(2, "INVOICE", "orgid", mbo.getString("orgid"));
			} else {
				sqf.setObject(2, "INVOICE", "orgid", mbo.getProfile().getInsertOrg());
			}
			setListCriteria(sqf.format());
		}
		
		return super.getList();
	}
}
