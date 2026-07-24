package cc.openxiot.wematrix.device.loach._sprinkler._fff._irrigationsystem;

import cc.openxiot.wematrix.device.loach._sprinkler._fff._irrigationsystem.properties.PropertyActive;
import cc.openxiot.wematrix.device.loach._sprinkler._fff._irrigationsystem.properties.PropertyProgramMode;
import cc.openxiot.wematrix.device.loach._sprinkler._fff._irrigationsystem.properties.PropertyInUse;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: 灌溉系统
 */
public class ServiceIrrigationSystem extends ServiceController {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:service:irrigation-system:000000cf:loach:fff:1";

    public ServiceIrrigationSystem() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "灌溉系统");

        super.properties().put(PropertyActive.IID, new PropertyActive());
        super.properties().put(PropertyProgramMode.IID, new PropertyProgramMode());
        super.properties().put(PropertyInUse.IID, new PropertyInUse());


    }

    /**
     * Property: 活动状态
     */
    public PropertyActive _property_active() {
        return (PropertyActive) properties().get(PropertyActive.IID);
    }

    /**
     * Property: 程序模式
     */
    public PropertyProgramMode _property_program_mode() {
        return (PropertyProgramMode) properties().get(PropertyProgramMode.IID);
    }

    /**
     * Property: 使用中
     */
    public PropertyInUse _property_in_use() {
        return (PropertyInUse) properties().get(PropertyInUse.IID);
    }

}
