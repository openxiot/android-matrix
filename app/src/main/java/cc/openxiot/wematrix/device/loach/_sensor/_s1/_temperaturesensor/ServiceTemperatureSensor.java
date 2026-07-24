package cc.openxiot.wematrix.device.loach._sensor._s1._temperaturesensor;

import cc.openxiot.wematrix.device.loach._sensor._s1._temperaturesensor.properties.PropertyCurrentTemperature;
import cc.openxiot.wematrix.device.loach._sensor._s1._temperaturesensor.properties.PropertyStatusLowBattery;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: 温度传感器
 */
public class ServiceTemperatureSensor extends ServiceController {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:service:temperature-sensor:0000008a:loach:s1:1";

    public ServiceTemperatureSensor() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "温度传感器");

        super.properties().put(PropertyCurrentTemperature.IID, new PropertyCurrentTemperature());
        super.properties().put(PropertyStatusLowBattery.IID, new PropertyStatusLowBattery());


    }

    /**
     * Property: 当前温度
     */
    public PropertyCurrentTemperature _property_current_temperature() {
        return (PropertyCurrentTemperature) properties().get(PropertyCurrentTemperature.IID);
    }

    /**
     * Property: 低电量
     */
    public PropertyStatusLowBattery _property_status_low_battery() {
        return (PropertyStatusLowBattery) properties().get(PropertyStatusLowBattery.IID);
    }

}
