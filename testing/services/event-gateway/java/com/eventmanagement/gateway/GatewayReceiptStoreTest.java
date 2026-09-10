package com.eventmanagement.gateway;
import org.junit.jupiter.api.Test;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
class GatewayReceiptStoreTest {
 @Test void storageFailureNeverReturnsAcceptedOrReceipt() throws Exception {
  DataSource ds=(DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{throw new SQLException("offline");});
  var mapper=new ObjectMapper();var store=new GatewayReceiptStore(ds,mapper);
  try(var context=new DefaultCamelContext()){
   var exchange=new DefaultExchange(context);exchange.getMessage().setBody("{raw}".getBytes());
   assertThrows(SQLException.class,()->store.capture(exchange));assertNull(exchange.getProperty("gatewayReceiptId"));
   exchange.setProperty(org.apache.camel.Exchange.EXCEPTION_CAUGHT,new SQLException("private connection details"));
   new GatewayCollectionFailure(store,mapper).respond(exchange);
   assertEquals(503,exchange.getMessage().getHeader(org.apache.camel.Exchange.HTTP_RESPONSE_CODE));
   var result=mapper.readTree(exchange.getMessage().getBody(String.class));assertFalse(result.path("accepted").asBoolean());assertFalse(result.has("receiptId"));assertFalse(result.toString().contains("private"));
  }
 }
}
