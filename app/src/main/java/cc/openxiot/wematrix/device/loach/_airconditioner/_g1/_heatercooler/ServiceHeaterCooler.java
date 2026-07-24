package cc.openxiot.wematrix.device.loach._airconditioner._g1._heatercooler;

import cc.openxiot.wematrix.device.loach._airconditioner._g1._heatercooler.properties.PropertyHeatingThresholdTemperature;
import cc.openxiot.wematrix.device.loach._airconditioner._g1._heatercooler.properties.PropertyRotationSpeed;
import cc.openxiot.wematrix.device.loach._airconditioner._g1._heatercooler.properties.PropertyActive;
import cc.openxiot.wematrix.device.loach._airconditioner._g1._heatercooler.properties.PropertyCurrentHeaterCoolerState;
import cc.openxiot.wematrix.device.loach._airconditioner._g1._heatercooler.properties.PropertyTargetHeaterCoolerState;
import cc.openxiot.wematrix.device.loach._airconditioner._g1._heatercooler.properties.PropertyCurrentTemperature;
import cc.openxiot.wematrix.device.loach._airconditioner._g1._heatercooler.properties.PropertyCoolingThresholdTemperature;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: 加热器/冷却器
 */
public class ServiceHeaterCooler extends ServiceController {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:service:heater-cooler:000000bc:loach:g1:1";

    public ServiceHeaterCooler() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "加热器/冷却器");

        super.properties().put(PropertyHeatingThresholdTemperature.IID, new PropertyHeatingThresholdTemperature());
        super.properties().put(PropertyRotationSpeed.IID, new PropertyRotationSpeed());
        super.properties().put(PropertyActive.IID, new PropertyActive());
        super.properties().put(PropertyCurrentHeaterCoolerState.IID, new PropertyCurrentHeaterCoolerState());
        super.properties().put(PropertyTargetHeaterCoolerState.IID, new PropertyTargetHeaterCoolerState());
        super.properties().put(PropertyCurrentTemperature.IID, new PropertyCurrentTemperature());
        super.properties().put(PropertyCoolingThresholdTemperature.IID, new PropertyCoolingThresholdTemperature());


    }

    /**
     * Property: 制热阈值
     */
    public PropertyHeatingThresholdTemperature _property_heating_threshold_temperature() {
        return (PropertyHeatingThresholdTemperature) properties().get(PropertyHeatingThresholdTemperature.IID);
    }

    /**
     * Property: 旋转速度
     */
    public PropertyRotationSpeed _property_rotation_speed() {
        return (PropertyRotationSpeed) properties().get(PropertyRotationSpeed.IID);
    }

    /**
     * Property: 活动状态
     */
    public PropertyActive _property_active() {
        return (PropertyActive) properties().get(PropertyActive.IID);
    }

    /**
     * Property: 当前状态
     */
    public PropertyCurrentHeaterCoolerState _property_current_heater_cooler_state() {
        return (PropertyCurrentHeaterCoolerState) properties().get(PropertyCurrentHeaterCoolerState.IID);
    }

    /**
     * Property: 目标状态
     */
    public PropertyTargetHeaterCoolerState _property_target_heater_cooler_state() {
        return (PropertyTargetHeaterCoolerState) properties().get(PropertyTargetHeaterCoolerState.IID);
    }

    /**
     * Property: 当前温度
     */
    public PropertyCurrentTemperature _property_current_temperature() {
        return (PropertyCurrentTemperature) properties().get(PropertyCurrentTemperature.IID);
    }

    /**
     * Property: 制冷阈值
     */
    public PropertyCoolingThresholdTemperature _property_cooling_threshold_temperature() {
        return (PropertyCoolingThresholdTemperature) properties().get(PropertyCoolingThresholdTemperature.IID);
    }

}
