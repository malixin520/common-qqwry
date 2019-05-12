package xin.common;

import org.junit.Assert;
import org.junit.Test;
import xin.common.qqwry.IPLocation;
import xin.common.qqwry.Location;

/**
 * <pre>
 * Test Ip to Location
 * </pre>
 *
 * @author lixin_ma@outlook.com
 * @since 2019/5/12 13:41
 */
public class TestLocation {

    @Test
    public void test(){
        try {
            final IPLocation ipLocation = new IPLocation(getClass().getResource("/qqwry.dat").getFile());
            Location location = ipLocation.fetchIPLocation("127.0.0.1");
            Assert.assertNotNull(location);
            Assert.assertNotNull(location.getRegion());
            Assert.assertNotNull(location.getOperator());
            System.out.println("127.0.0.1 -> " + location.getRegion() + "-" + location.getOperator());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testPathNull(){
        try {
            final IPLocation ipLocation = new IPLocation(null);
            ipLocation.fetchIPLocation(null);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testIpNull(){
        try {
            final IPLocation ipLocation = new IPLocation(getClass().getResource("/qqwry.dat").getFile());
            ipLocation.fetchIPLocation(null);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }
    }

}
