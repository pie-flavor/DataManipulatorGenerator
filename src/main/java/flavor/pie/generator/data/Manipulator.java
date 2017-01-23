package flavor.pie.generator.data;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

import java.util.Collections;
import java.util.List;

@ConfigSerializable
public class Manipulator {
    public static final TypeToken<Manipulator> type = TypeToken.of(Manipulator.class);
    @Setting
    List<ManipulatorField> fields;
    @Setting("class")
    String classname;
    @Setting("package")
    String packagename;
    @Setting("key-class")
    String keyClass;
    @Setting
    List<String> imports = Collections.EMPTY_LIST;
    @Setting("plugin-id")
    String pluginId;

    @ConfigSerializable
    public static class ManipulatorField {
        @Setting
        String type;
        @Setting
        String fullType;
        @Setting("transient")
        boolean isTransient = false;
        @Setting
        ManipulatorKey key = new ManipulatorKey();
        @Setting("default")
        String defaultValue;
        @Setting
        boolean optional = false;
        @Setting
        String name;
        String boxedType;
        String uppercase;
        String nonGeneric;
        DataManipulatorGenerator.ValueType valueType;
        String valueName;
        String innerValue;
        String nonGenericInnerValue;
    }

    @ConfigSerializable
    public static class ManipulatorKey {
        @Setting
        String name;
        @Setting("data-query")
        String dataQuery;
        @Setting
        String id;
        @Setting("display-name")
        String displayName;
        String fullName;
        String valueType;
        String itemType;
    }
}
