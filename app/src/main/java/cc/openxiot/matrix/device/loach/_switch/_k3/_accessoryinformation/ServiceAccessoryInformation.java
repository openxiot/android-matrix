package cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation;

import cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation.properties.PropertyFirmwareRevision;
import cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation.properties.PropertyIdentify;
import cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation.properties.PropertyManufacturer;
import cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation.properties.PropertyModel;
import cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation.properties.PropertyName;
import cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation.properties.PropertySerialNumber;
import cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation.properties.PropertyAccessoryFlags;
import cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation.properties.PropertyHardwareRevision;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: 配件信息
 */
public class ServiceAccessoryInformation extends ServiceController {

    public static final int IID = 1;
    public static final String TYPE = "urn:homekit-spec:service:accessory-information:0000003e:loach:k3:1";

    public ServiceAccessoryInformation() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "配件信息");

        super.properties().put(PropertyFirmwareRevision.IID, new PropertyFirmwareRevision());
        super.properties().put(PropertyIdentify.IID, new PropertyIdentify());
        super.properties().put(PropertyManufacturer.IID, new PropertyManufacturer());
        super.properties().put(PropertyModel.IID, new PropertyModel());
        super.properties().put(PropertyName.IID, new PropertyName());
        super.properties().put(PropertySerialNumber.IID, new PropertySerialNumber());
        super.properties().put(PropertyAccessoryFlags.IID, new PropertyAccessoryFlags());
        super.properties().put(PropertyHardwareRevision.IID, new PropertyHardwareRevision());


    }

    /**
     * Property: 固件版本
     */
    public PropertyFirmwareRevision _property_firmware_revision() {
        return (PropertyFirmwareRevision) properties().get(PropertyFirmwareRevision.IID);
    }

    /**
     * Property: 识别
     */
    public PropertyIdentify _property_identify() {
        return (PropertyIdentify) properties().get(PropertyIdentify.IID);
    }

    /**
     * Property: 制造商
     */
    public PropertyManufacturer _property_manufacturer() {
        return (PropertyManufacturer) properties().get(PropertyManufacturer.IID);
    }

    /**
     * Property: 型号
     */
    public PropertyModel _property_model() {
        return (PropertyModel) properties().get(PropertyModel.IID);
    }

    /**
     * Property: 名称
     */
    public PropertyName _property_name() {
        return (PropertyName) properties().get(PropertyName.IID);
    }

    /**
     * Property: 序列号
     */
    public PropertySerialNumber _property_serial_number() {
        return (PropertySerialNumber) properties().get(PropertySerialNumber.IID);
    }

    /**
     * Property: 附件标志
     */
    public PropertyAccessoryFlags _property_accessory_flags() {
        return (PropertyAccessoryFlags) properties().get(PropertyAccessoryFlags.IID);
    }

    /**
     * Property: 硬件版本
     */
    public PropertyHardwareRevision _property_hardware_revision() {
        return (PropertyHardwareRevision) properties().get(PropertyHardwareRevision.IID);
    }

}
