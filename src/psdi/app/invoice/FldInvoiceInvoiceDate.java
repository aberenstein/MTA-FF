package psdi.app.invoice;

import java.rmi.RemoteException;
import java.util.Date;

import psdi.util.MXApplicationException;
import psdi.util.MXException;
import psdi.mbo.Mbo;
import psdi.mbo.MboRemote;
import psdi.mbo.MboSetRemote;
import psdi.mbo.MboValue;
import psdi.mbo.MboValueAdapter;

public class FldInvoiceInvoiceDate extends MboValueAdapter
{
	public FldInvoiceInvoiceDate(MboValue mbv)
	{
		super(mbv);
	}
  
	public void action() throws MXException, RemoteException
	{
		if (getMboValue().isNull()) {
			return;
		}
    
		Mbo invoice = getMboValue().getMbo();

		boolean updateGLPostDate = invoice.getMboServer().getMaxVar().getBoolean("UPDATEGLPSTDATE", invoice.getString("orgID"));
    
		if (updateGLPostDate) {
			invoice.setValue("glpostdate", getMboValue().getDate(), 11L);
		}
	}

	public void validate() throws MXException, RemoteException
	{
		super.validate();
	    
		if (getMboValue().isNull()) {
			return;
		}
		    
		/**********************************************************************************/
		/* Validación de la fecha de factura contra la fecha de aprobación de la OC       */
		/**********************************************************************************/
		    		    
		// Obtengo un Mbo para operar sobre la BD
		Invoice invoiceMbo = (Invoice)getMboValue().getMbo();

		// Me fijo si hay nro de OC cargado, si no hay itero sobre las lineas de la factura 
		String poNum = getMboValue("PONUM").getString();
			    
		if (poNum != null && !poNum.isEmpty()) {
			// Hay cargada una OC: validamos
			FldInvoiceInvoiceDate.validatePO(invoiceMbo, true);
		} else {
			// Obtengo las lineas de la factura
			MboSetRemote invoiceLineSet = invoiceMbo.getMboSet("INVOICELINE");
				    
			// Itero sobre las lineas de la factura y verifico cada OC
			for(MboRemote currMbo=invoiceLineSet.moveFirst(); currMbo!=null; currMbo=invoiceLineSet.moveNext())
			{
				// Voy por la OC ////////////////
				MboSetRemote poSet = currMbo.getMboSet("PO");

				if (poSet.isEmpty()) {
					// Esta linea no tiene OC: nada que validar
					continue;
				} else {
					// Esta linea tiene OC: la obtengo
					MboRemote poMbo = poSet.getMbo(0);
					// Ya tengo la OC ///////////////

					// Valido
					FldInvoiceInvoiceDate.validatePO(invoiceMbo, poMbo, true);
				}
			}
		}
		    
		/**********************************************************************************/
		/* Fin validación de la fecha de factura contra la fecha de aprobación de la OC   */
		/**********************************************************************************/

	}
  
	public static void validatePO(Invoice invoiceMbo, MboRemote poMbo, boolean validatingDate) throws MXException, RemoteException
	{
		// Obtengo la fecha de factura
		Date invoiceDate = invoiceMbo.getDate("INVOICEDATE");
				
		if (invoiceDate == null) {
			// No tengo fecha de factura. No hay nada que validar.
			return;
		}

		if (invoiceDate.after(new Date())){
			throw new MXApplicationException("MTA", "FechaInvalida");
		}
		// Ya tengo la fecha de factura //////////////////////////
			    
	    // Ahora con la OC obtengo la fecha de aprobación de la tabla POSTATUS
	    MboSetRemote poStatusSet = poMbo.getMboSet("POSTATUS");

	    String whereClause = "REVISIONNUM=" + Integer.toString(poMbo.getInt("REVISIONNUM"));
	    whereClause += " AND ";
	    whereClause += "STATUS='APROB'";
	    
	    poStatusSet.setWhere(whereClause);

	    if (poStatusSet.isEmpty()) {
	    	// Si es vacio es que la OC no está aprobada por lo que el nro de OC es inválido
	    	throw new MXApplicationException("po", "InvalidPONum");
	    }
	    
	    MboRemote poStatusMbo = poStatusSet.getMbo(0);

	    Date poAprobDate = poStatusMbo.getDate("CHANGEDATE");
	     
	    if (poAprobDate == null) {
	    	// Si no tiene indicada fecha de aprobaciôn entonces la OC es inválida
	    	throw new MXApplicationException("po", "InvalidPONum");
	    }
	    // Ya tengo la fecha de aprobaciôn de la OC ///////////////

	    // Valido si la fecha de factura es igual o posterior a la fecha en cuestión  
	    if (invoiceDate.before(poAprobDate)){
	    	// La fecha de factura es anterior a la fecha en cuestión: levanto una excepción
	    	if (validatingDate) {
			      throw new MXApplicationException("MTA", "FechaInvalida");
	    	} else {
			      throw new MXApplicationException("MTA", "OCInvalida");
	    	}	    		
	    }
	}

	public static void validatePO(Invoice invoiceMbo, boolean validatingDate) throws MXException, RemoteException
	{
		// Obtengo la fecha de factura
		Date invoiceDate = invoiceMbo.getDate("INVOICEDATE");
				
		if (invoiceDate == null) {
			// No tengo fecha de factura. No hay nada que validar.
			return;
		}

		if (invoiceDate.after(new Date())){
			throw new MXApplicationException("MTA", "FechaInvalida");
		}
		// Ya tengo la fecha de factura //////////////////////////
			    
		String poNum = invoiceMbo.getString("PONUM");
		
		if (poNum == null || poNum.isEmpty()) {
			// No tengo Nro. de OC. No hay nada que validar.
			return;
		}

		// Voy por la OC ////////////////
		MboSetRemote poSet = invoiceMbo.getMboSet("PO");
			    
		String whereClause = "STATUS NOT IN (SELECT VALUE FROM SYNONYMDOMAIN WHERE DOMAINID='POSTATUS' AND MAXVALUE IN ('REVISD','PNDREV'))";

		poSet.setWhere(whereClause);
		
		if (poSet.isEmpty()) {
			// Si es vacio es que el nro de OC es inválido
			throw new MXApplicationException("po", "InvalidPONum");
		}
			    
		MboRemote poMbo = poSet.getMbo(0);
		// Ya tengo la OC ///////////////

		// Valido
		FldInvoiceInvoiceDate.validatePO(invoiceMbo, poMbo, validatingDate);
	}
}
