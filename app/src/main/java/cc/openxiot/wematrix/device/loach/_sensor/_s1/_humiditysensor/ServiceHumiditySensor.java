package cc.openxiot.wematrix.device.loach._sensor._s1._humiditysensor;

import cc.openxiot.wematrix.device.loach._sensor._s1._humiditysensor.properties.PropertyCurrentRelativeHumidity;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: 湿度传感器
 */
public class ServiceHumiditySensor extends ServiceController {

    public static final int IID = 12;
    public static final String TYPE = "urn:homekit-spec:service:humidity-sensor:00000082:loach:s1:1";

    public ServiceHumiditySensor() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "湿度传感器");

        super.properties().put(PropertyCurrentRelativeHumidity.IID, new PropertyCurrentRelativeHumidity());


    }

    /**
     * Property: 当前湿度
     */
    public PropertyCurrentRelativeHumidity _property_current_relative_humidity() {
        return (PropertyCurrentRelativeHumidity) properties().get(PropertyCurrentRelativeHumidity.IID);
    }

}
