package flavor.pie.generator.data;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

public class DataManipulatorGenerator {
    public static void main(String args[]) throws Exception {
        if (args.length == 0) {
            if (System.console() == null) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                JFileChooser chooser = new JFileChooser();
                chooser.addChoosableFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.getName().endsWith(".conf");
                    }

                    @Override
                    public String getDescription() {
                        return "HOCON configuration files (*.conf)";
                    }
                });
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.addChoosableFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return true;
                    }

                    @Override
                    public String getDescription() {
                        return "All files (*.*)";
                    }
                });
                chooser.setMultiSelectionEnabled(true);
                chooser.showOpenDialog(null);
                Arrays.stream(chooser.getSelectedFiles()).forEach(DataManipulatorGenerator::generate);
            } else {
                System.out.println("Enter a list of files; end with an empty line");
                Scanner scanner = new Scanner(System.in);
                List<String> files = new ArrayList<>();
                while (scanner.hasNext()) {
                    String s = scanner.next();
                    if (s.equals("")) {
                        break;
                    }
                    files.add(s);
                }
                for (String s : files) {
                    generate(new File(s));
                }
            }
        } else {
            for (String arg : args) {
                generate(new File(arg));
            }
        }
    }

    public static void generate(File file) {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().setFile(file).build();
        String filename = file.getName();
        String t1 = "    ";
        String t2 = "        ";
        String t3 = "            ";
        String t4 = "                ";
        String t5 = "                    ";
        Manipulator manipulator;
        try {
            manipulator = loader.load().getValue(Manipulator.type);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
        Set<String> primitives = ImmutableSet.of("I", "Z", "D", "F", "L", "C", "S", "B");
        manipulator.fields.stream().filter(f -> primitives.contains(f.type)).forEach(f -> {
            switch (f.type) {
                case "I":
                    f.fullType = "int";
                    f.boxedType = "Integer";
                    break;
                case "Z":
                    f.fullType = "boolean";
                    f.boxedType = "Boolean";
                    break;
                case "D":
                    f.fullType = "double";
                    f.boxedType = "Double";
                    break;
                case "F":
                    f.fullType = "float";
                    f.boxedType = "Float";
                    break;
                case "L":
                    f.fullType = "long";
                    f.boxedType = "Long";
                    break;
                case "C":
                    f.fullType = "char";
                    f.boxedType = "Character";
                    break;
                case "S":
                    f.fullType = "short";
                    f.boxedType = "Short";
                    break;
                case "B":
                    f.fullType = "byte";
                    f.boxedType = "Byte";
                    break;
            }
        });
        manipulator.fields.stream().filter(f -> f.fullType == null).forEach(f -> f.fullType = f.type.replaceAll(".*[.](.*)", "$1"));
        manipulator.fields.stream().filter(f -> !primitives.contains(f.type)).forEach(f -> f.boxedType = f.fullType);
        manipulator.fields.stream().filter(f -> f.key.name == null).forEach(f -> f.key.name = caps(f.name));
        manipulator.fields.stream().filter(f -> f.key.id == null).forEach(f -> f.key.id = (manipulator.pluginId != null ? manipulator.pluginId + ":" : "") + f.key.name.replace("_", "").toLowerCase());
        manipulator.fields.stream().filter(f -> f.key.dataQuery == null).forEach(f -> f.key.dataQuery = String.join("", Arrays.stream(f.key.name.split("_")).map(s -> s.charAt(0) + s.substring(1).toLowerCase()).collect(Collectors.toList())));
        manipulator.fields.stream().filter(f -> f.key.displayName == null).forEach(f -> f.key.displayName = String.join(" ", Arrays.stream(f.key.name.split("_")).map(s -> s.charAt(0) + s.substring(1).toLowerCase()).collect(Collectors.toList())));
        manipulator.fields.forEach(f -> {
            f.uppercase = String.format("%s%s", Character.toUpperCase(f.name.charAt(0)), f.name.substring(1));
            f.nonGeneric = f.fullType.replaceAll("<.*","");
            if (!f.optional) {
                switch (f.nonGeneric) {
                    case "List":
                        f.valueType = ValueType.LIST;
                        f.valueName = f.fullType.replaceFirst("List", "ListValue");
                        f.innerValue = f.fullType.replaceFirst("List<(.*)>","$1");
                        f.nonGenericInnerValue = f.innerValue.replaceFirst("<.*", "");
                        break;
                    case "Map":
                        f.valueType = ValueType.MAP;
                        f.valueName = f.fullType.replaceFirst("Map", "MapValue");
                        break;
                    case "Set":
                        f.valueType = ValueType.SET;
                        f.valueName = f.fullType.replaceFirst("Set", "SetValue");
                        f.innerValue = f.fullType.replaceAll("Set<(.*)>", "$1");
                        f.nonGenericInnerValue = f.innerValue.replaceFirst("<.*", "");
                        break;
                    default:
                        f.valueType = ValueType.REGULAR;
                        f.valueName = String.format("Value<%s>", f.boxedType);
                }
                f.key.itemType = f.boxedType;
                f.key.valueType = f.valueName;
            } else {
                f.valueType = ValueType.OPTIONAL;
                f.key.itemType = String.format("Optional<%s>", f.boxedType);
                f.key.valueType = String.format("OptionalValue<%s>", f.boxedType);
                f.valueName = String.format("OptionalValue<%s>", f.boxedType);
            }
        });

        List<String> imports = Lists.newArrayList("org.spongepowered.api.Sponge",
                "org.spongepowered.api.data.DataContainer",
                "org.spongepowered.api.data.DataHolder",
                "org.spongepowered.api.data.DataView",
                "org.spongepowered.api.data.manipulator.DataManipulatorBuilder",
                "org.spongepowered.api.data.manipulator.immutable.common.AbstractImmutableData",
                "org.spongepowered.api.data.manipulator.mutable.common.AbstractData",
                "org.spongepowered.api.data.merge.MergeFunction",
                "org.spongepowered.api.data.persistence.AbstractDataBuilder",
                "org.spongepowered.api.data.persistence.InvalidDataException",
                "java.util.Optional",
                "javax.annotation.Generated");
        List<String> keyImports = Lists.newArrayList("com.google.common.reflect.TypeToken",
                "org.spongepowered.api.data.DataQuery",
                "org.spongepowered.api.data.key.Key",
                "org.spongepowered.api.data.key.KeyFactory",
                "javax.annotation.Generated");
        manipulator.fields.stream().filter(f -> f.optional).findAny().ifPresent(f -> {
            imports.add("java.util.Optional");
            imports.add("javax.annotation.Nullable");
            keyImports.add("java.util.Optional");
        });
        manipulator.fields.stream().filter(f -> f.defaultValue == null).forEach(f -> {
            if (f.optional) return;
            switch (f.type) {
                case "java.lang.String":
                case "String": f.defaultValue = "\"\""; break;
                case "java.util.Map":
                    f.defaultValue = "Collections.emptyMap()";
                    imports.add("java.util.Collections"); break;
                case "java.util.List":
                    f.defaultValue = "Collections.emptyList()";
                    imports.add("java.util.Collections"); break;
                case "java.util.Set":
                    f.defaultValue = "Collections.emptySet()";
                    imports.add("java.util.Collections"); break;
                case "I": f.defaultValue = "0"; break;
                case "D": f.defaultValue = "0.0d"; break;
                case "F": f.defaultValue = "0.0f"; break;
                case "L": f.defaultValue = "0l"; break;
                case "Z": f.defaultValue = "false"; break;
                case "S": f.defaultValue = "0"; break;
                case "B": f.defaultValue = "0"; break;
                case "C": f.defaultValue = "'\\u0000'"; break;
                case "java.math.BigDecimal":
                case "java.math.BigInteger":
                case "com.flowpowered.math.vector.Vector2i":
                case "com.flowpowered.math.vector.Vector2d":
                case "com.flowpowered.math.vector.Vector3i":
                case "com.flowpowered.math.vector.Vector2l":
                case "com.flowpowered.math.vector.Vector3l":
                case "com.flowpowered.math.vector.Vector3d": f.defaultValue = f.nonGeneric + ".ZERO"; break;
                case "org.spongepowered.api.data.DataContainer":
                    f.defaultValue = "new MemoryDataContainer()";
                    imports.add("org.spongepowered.api.data.MemoryDataContainer"); break;
                case "java.util.UUID": f.defaultValue = "UUID.fromString(\"00000000-0000-0000-0000-000000000000\")"; break;
                case "org.spongepowered.api.service.economy.Currency":
                    f.defaultValue = "Sponge.getServiceManager().provideUnchecked(EconomyService.class).getDefaultCurrency()";
                    imports.add("org.spongepowered.api.service.economy.EconomyService"); break;
                case "org.spongepowered.api.item.ItemType":
                    f.defaultValue = "ItemTypes.NONE";
                    imports.add("org.spongepowered.api.item.ItemTypes"); break;
                case "org.spongepowered.api.block.BlockType":
                    f.defaultValue = "BlockTypes.AIR";
                    imports.add("org.spongepowered.api.block.BlockTypes"); break;
                case "org.spongepowered.api.statistic.Achievement":
                    f.defaultValue = "Achievements.OPEN_INVENTORY";
                    imports.add("org.spongepowered.api.statistic.Achievements"); break;
                case "org.spongepowered.api.data.type.ArmorType":
                    f.defaultValue = "ArmorTypes.LEATHER";
                    imports.add("org.spongepowered.api.data.type.ArmorTypes"); break;
                case "org.spongepowered.api.world.biome.BiomeType":
                    f.defaultValue = "BiomeTypes.PLAINS";
                    imports.add("org.spongepowered.api.world.biome.BiomeTypes"); break;
                case "org.spongepowered.api.boss.BossBarColor":
                    f.defaultValue = "BossBars.PURPLE";
                    imports.add("org.spongepowered.api.boss.BossBarColors"); break;
                case "org.spongepowered.api.data.type.DyeColor":
                    f.defaultValue = "DyeColors.WHITE";
                    imports.add("org.spongepowered.api.data.type.DyeColors"); break;
                case "org.spongepowered.api.item.Enchantment":
                    f.defaultValue = "Enchantments.SHARPNESS";
                    imports.add("org.spongepowered.api.item.Enchantments"); break;
                case "org.spongepowered.api.entity.EntityType":
                    f.defaultValue = "EntityTypes.PIG";
                    imports.add("org.spongepowered.api.entity.EntityTypes"); break;
                case "org.spongepowered.api.item.FireworkShape":
                    f.defaultValue = "FireworkShapes.BURST";
                    imports.add("org.spongepowered.api.item.FireworkShapes"); break;
                case "org.spongepowered.api.extra.fluid.FluidType":
                    f.defaultValue = "FluidTypes.WATER";
                    imports.add("org.spongepowered.api.extra.fluid.FluidTypes"); break;
                case "org.spongepowered.api.world.PortalAgentType":
                    f.defaultValue = "PortalAgentTypes.DEFAULT";
                    imports.add("org.spongepowered.api.world.PortalAgentTypes"); break;
                case "org.spongepowered.api.effect.potion.PotionEffectType":
                    f.defaultValue = "PotionEffectTypes.SPEED";
                    imports.add("org.spongepowered.api.effect.potion.PotionEffectTypes"); break;
                case "org.spongepowered.api.effect.sound.SoundType":
                    f.defaultValue = "SoundTypes.ENTITY_EXPERIENCE_ORB_PICKUP";
                    imports.add("org.spongepowered.api.effect.sound.SoundTypes"); break;
                case "org.spongepowered.api.text.format.TextColor":
                    f.defaultValue = "TextColors.WHITE";
                    imports.add("org.spongepowered.api.text.format.TextColors"); break;
                case "org.spongepowered.api.data.type.ToolType":
                    f.defaultValue = "ToolTypes.WOOD";
                    imports.add("org.spongepowered.api.data.type.ToolTypes"); break;
                case "org.spongepowered.api.text.Text": f.defaultValue = "Text.of()"; break;
            }
        });
        String n = System.getProperty("line.separator");
        String classname = manipulator.classname == null ? filename.replaceAll("(.*)[.].*","$1").replaceAll("\\W", "") : manipulator.classname;
        File output = new File(classname + ".java");
        if (output.exists()) {
            if (!output.delete()) {
                System.err.printf("Could not delete file %s!", output);
                return;
            }
        }
        String keyclass = manipulator.keyClass == null ? classname.replaceAll("Data(Manipulator$|$)","").concat("Keys") : manipulator.keyClass;
        manipulator.fields.stream().map(f -> f.key).forEach(k -> k.fullName = String.format("%s.%s", keyclass, k.name));
        File keyout = new File(keyclass + ".java");
        if (keyout.exists()) {
            if (!keyout.delete()) {
                System.err.printf("Could not delete file %s!", keyout);
            }
        }
        try (PrintWriter writer = new PrintWriter(output); PrintWriter keys = new PrintWriter(keyout)) {
            //package
            if (manipulator.packagename != null) {
                writer.printf("package %s;%s", manipulator.packagename, n);
                writer.println();
            }
            //value-specific imports
            List<String> valueImports = manipulator.fields.stream().filter(f -> !f.type.equals(f.fullType)).map(f -> f.type)
                    .filter(t -> !primitives.contains(t)).filter(t -> !t.startsWith("java.lang")).collect(Collectors.toList());
            imports.addAll(valueImports);
            keyImports.addAll(valueImports);
            List<String> mutableValueImports = manipulator.fields.stream().map(f -> f.valueType.getMutableType()).collect(Collectors.toList());
            imports.addAll(mutableValueImports);
            keyImports.addAll(mutableValueImports);
            imports.addAll(manipulator.fields.stream().map(f -> f.valueType.getImmutableType()).collect(Collectors.toList()));
            imports.addAll(manipulator.imports);
            keyImports.addAll(manipulator.imports);
            imports.stream().distinct().sorted().forEach(s ->
                    //import statement
                    writer.printf("import %s;%s", s, n));
            writer.println();

            //hello world
            writer.printf("@Generated(value = \"%s\", date = \"%s\")%s", DataManipulatorGenerator.class.getName(), Instant.now(), n);

            //class header
            writer.printf("public class %1$s extends AbstractData<%1$s, %1$s.Immutable> {%2$s%2$s", classname, n);

            //fields
            manipulator.fields.forEach(f ->
                    writer.printf("%sprivate %s %s;%s", t1, f.fullType, f.name, n));
            writer.println();

            //default constructor
            writer.printf("%s{%s", t1, n);
            writer.printf("%sregisterGettersAndSetters();%s", t2, n);
            writer.printf("%s}%s%2$s", t1, n);

            //no-args constructor
            writer.printf("%s%s() {%s", t1, classname, n);
            manipulator.fields.stream().filter(f -> f.defaultValue != null).forEach(f ->
                    //value assignment
                    writer.printf("%s%s = %s;%s", t2, f.name, f.defaultValue, n));
            writer.printf("%s}%s%2$s", t1, n);

            //full constructor
            writer.printf("%s%s(%s) {%s", t1, classname, Joiner.on(", ").join(manipulator.fields.stream()
                    .map(f -> String.format("%s %s", f.fullType, f.name)).collect(Collectors.toList())), n);
            manipulator.fields.forEach(f ->
                    //assignment
                    writer.printf("%sthis.%s = %2$s;%s", t2, f.name, n));
            writer.printf("%s}%s%2$s", t1, n);

            //registerGettersAndSetters()
            writer.printf("%s@Override%s%1$sprotected void registerGettersAndSetters() {%2$s", t1, n);
            manipulator.fields.forEach(f -> {
                //registerFieldGetter()
                writer.printf("%sregisterFieldGetter(%s, this::%s%s);%s", t2, f.key.fullName,
                        f.type.equals("Z") ? "is" : "get", f.uppercase, n);
                //registerFieldSetter()
                if (f.optional) {
                    writer.printf("%sregisterFieldSetter(%s, v -> set%s(v.get()));%s", t2, f.key.fullName, f.uppercase, n);
                } else {
                    writer.printf("%sregisterFieldSetter(%s, this::set%s);%s", t2, f.key.fullName, f.uppercase, n);
                }
                //registerKeyValue()
                writer.printf("%sregisterKeyValue(%s, this::%s);%s", t2, f.key.fullName, f.name, n);
            });
            writer.printf("%s}%s%2$s", t1, n);

            //field getters and setters
            manipulator.fields.forEach(f -> {
                //getter
                writer.printf("%spublic %s %s%s() {%s", t1, f.optional ? String.format("Optional<%s>", f.boxedType) : f.fullType, f.type.equals("Z") ? "is" : "get", f.uppercase, n);
                writer.printf("%sreturn %s;%s", t2, f.optional ? String.format("Optional.ofNullable(%s)", f.name) : f.name, n);
                writer.printf("%s}%s%2$s", t1, n);
                //setter
                writer.printf("%spublic void set%s(%s%s %s) {%s", t1, f.uppercase, f.optional ? "@Nullable " : "", f.fullType, f.name, n);
                writer.printf("%sthis.%s = %2$s;%s", t2, f.name, n);
                writer.printf("%s}%s%2$s", t1, n);
                //value
                writer.printf("%spublic %s %s() {%s", t1, f.valueName, f.name, n);
                writer.printf("%sreturn Sponge.getRegistry().getValueFactory().create%s(%s, %s);%s", t2, f.valueType.getMutableName(), f.key.fullName, f.name, n);
                writer.printf("%s}%s%2$s", t1, n);
            });

            //fill()
            writer.printf("%s@Override%s", t1, n);
            writer.printf("%spublic Optional<%s> fill(DataHolder dataHolder, MergeFunction overlap) {%s", t1, classname, n);
            writer.printf("%sdataHolder.get(%s.class).ifPresent(that -> {%s", t2, classname, n);
            writer.printf("%s%s data = overlap.merge(this, that);%s", t4, classname, n);
            manipulator.fields.forEach(f ->
                    writer.printf("%sthis.%s = data.%2$s;%s", t4, f.name, n));
            writer.printf("%s});%s", t2, n);
            writer.printf("%sreturn Optional.of(this);%s%s}%2$s%2$s", t2, n, t1);

            //from() override
            writer.printf("%s@Override%s", t1, n);
            writer.printf("%spublic Optional<%s> from(DataContainer container) {%s", t1, classname, n);
            writer.printf("%sreturn from((DataView) container);%s", t2, n);
            writer.printf("%s}%s%2$s", t1, n);

            //from() impl
            writer.printf("%spublic Optional<%s> from(DataView container) {%s", t1, classname, n);
            manipulator.fields.stream().filter(f -> !f.isTransient).forEach(f -> {
                writer.printf("%scontainer.get", t2);
                switch (f.valueType) {
                    case MAP: writer.printf("Map(%s.getQuery())", f.key.fullName); break;
                    case LIST:
                        switch (f.nonGenericInnerValue) {
                            case "Integer": writer.printf("IntegerList(%s.getQuery())", f.key.fullName); break;
                            case "Boolean": writer.printf("BooleanList(%s.getQuery())", f.key.fullName); break;
                            case "Character": writer.printf("CharacterList(%s.getQuery())", f.key.fullName); break;
                            case "Double": writer.printf("DoubleList(%s.getQuery())", f.key.fullName); break;
                            case "Long": writer.printf("LongList(%s.getQuery())", f.key.fullName); break;
                            case "Short": writer.printf("ShortList(%s.getQuery())", f.key.fullName); break;
                            case "String": writer.printf("StringList(%s.getQuery())", f.key.fullName); break;
                            case "Byte": writer.printf("ByteList(%s.getQuery())", f.key.fullName); break;
                            case "Float": writer.printf("FloatList(%s.getQuery())", f.key.fullName); break;
                            default: writer.printf("ObjectList(%s.getQuery(), %s.class)", f.key.fullName, f.nonGenericInnerValue);
                        }
                        break;
                    case SET:
                        switch (f.nonGenericInnerValue) {
                            case "Integer": writer.printf("IntegerList(%s.getQuery())", f.key.fullName); break;
                            case "Boolean": writer.printf("BooleanList(%s.getQuery())", f.key.fullName); break;
                            case "Character": writer.printf("CharacterList(%s.getQuery())", f.key.fullName); break;
                            case "Double": writer.printf("DoubleList(%s.getQuery())", f.key.fullName); break;
                            case "Long": writer.printf("LongList(%s.getQuery())", f.key.fullName); break;
                            case "Short": writer.printf("ShortList(%s.getQuery())", f.key.fullName); break;
                            case "String": writer.printf("StringList(%s.getQuery())", f.key.fullName); break;
                            case "Byte": writer.printf("ByteList(%s.getQuery())", f.key.fullName); break;
                            case "Float": writer.printf("FloatList(%s.getQuery())", f.key.fullName); break;
                            default: writer.printf("ObjectList(%s.getQuery(), %s.class)", f.key.fullName, f.nonGenericInnerValue);
                        }
                        writer.print(".map(Set::new)");
                        break;
                    default:
                        switch (f.type) {
                            case "Z": writer.printf("Boolean(%s.getQuery())", f.key.fullName); break;
                            case "B": writer.printf("Byte(%s.getQuery())", f.key.fullName); break;
                            case "D": writer.printf("Double(%s.getQuery())", f.key.fullName); break;
                            case "F": writer.printf("Float(%s.getQuery())", f.key.fullName); break;
                            case "I": writer.printf("Int(%s.getQuery())", f.key.fullName); break;
                            case "L": writer.printf("Long(%s.getQuery())", f.key.fullName); break;
                            case "S": writer.printf("Short(%s.getQuery())", f.key.fullName); break;
                            case "C": writer.printf("Int(%s.getQuery()).map(i -> (char) i", f.key.fullName); break;
                            case "java.lang.String": writer.printf("String(%s.getQuery())", f.key.fullName); break;
                            default: writer.printf("Object(%s.getQuery(), %s.class)", f.key.fullName, f.nonGeneric);
                        }
                }
                if (f.valueType == ValueType.MAP) {
                    writer.printf("; //TODO%s", n);
                } else {
                    writer.printf(".ifPresent(v -> %s = v);%s", f.name, n);
                }
            });
            writer.printf("%sreturn Optional.of(this);%s", t2, n);
            writer.printf("%s}%s%2$s", t1, n);

            String argumentList = Joiner.on(", ").join(manipulator.fields.stream().map(f -> f.name).collect(Collectors.toList()));

            //copy()
            writer.printf("%s@Override%s", t1, n);
            writer.printf("%spublic %s copy() {%s", t1, classname, n);
            writer.printf("%sreturn new %s(%s);%s", t2, classname, argumentList, n);
            writer.printf("%s}%s%2$s", t1, n);

            //asImmutable()
            writer.printf("%s@Override%s%1$spublic Immutable asImmutable() {%2$s", t1, n);
            writer.printf("%sreturn new Immutable(%s);%s", t2, argumentList, n);
            writer.printf("%s}%s%2$s", t1, n);

            //getContentVersion()
            writer.printf("%s@Override%s%1$spublic int getContentVersion() {%2$s", t1, n);
            writer.printf("%sreturn 1;%s", t2, n);
            writer.printf("%s}%s%2$s", t1, n);

            //toContainer()
            writer.printf("%s@Override%s%1$spublic DataContainer toContainer() {%2$s", t1, n);
            writer.printf("%sreturn super.toContainer()", t2);
            manipulator.fields.stream().filter(f -> !f.isTransient).forEach(
                    f -> writer.printf("%s%s.set(%s.getQuery(), %s)", n, t4, f.key.fullName, f.name));
            writer.printf(";%s%s}%1$s%1$s", n, t1);

            //ImmutableDataManpiulator

            //hello world
            writer.printf("%s@Generated(value = \"%s\", date = \"%s\")%s", t1, DataManipulatorGenerator.class.getName(), Instant.now(), n);

            //class header
            writer.printf("%spublic static class Immutable extends AbstractImmutableData<Immutable, %s> {%s%3$s", t1, classname, n);

            //fields
            manipulator.fields.forEach(f ->
                    writer.printf("%sprivate %s %s;%s", t2, f.fullType, f.name, n));

            //default constructor
            writer.printf("%s{%s%sregisterGetters();%2$s%1$s}%2$s%2$s", t2, n, t3);

            //no-args constructor
            writer.printf("%sImmutable() {%s", t2, n);
            manipulator.fields.stream().filter(f -> f.defaultValue != null).forEach(f ->
                    //value assignment
                    writer.printf("%s%s = %s;%s", t3, f.name, f.defaultValue, n));
            writer.printf("%s}%s%2$s", t2, n);

            //full constructor
            writer.printf("%sImmutable(%s) {%s", t2, Joiner.on(", ").join(manipulator.fields.stream()
                    .map(f -> String.format("%s %s", f.fullType, f.name)).collect(Collectors.toList())), n);
            manipulator.fields.forEach(f ->
                    //assignment
                    writer.printf("%sthis.%s = %2$s;%s", t3, f.name, n));
            writer.printf("%s}%s%2$s", t2, n);

            //registerGetters()
            writer.printf("%s@Override%s%1$sprotected void registerGetters() {%2$s", t2, n);
            manipulator.fields.forEach(f -> {
                //registerFieldGetter()
                writer.printf("%sregisterFieldGetter(%s, this::%s%s);%s", t3, f.key.fullName,
                        f.type.equals("Z") ? "is" : "get", f.uppercase, n);
                //registerKeyValue()
                writer.printf("%sregisterKeyValue(%s, this::%s);%s", t3, f.key.fullName, f.name, n);
            });
            writer.printf("%s}%s%2$s", t2, n);

            //field getters
            manipulator.fields.forEach(f -> {
                //getter
                writer.printf("%spublic %s %s%s() {%s", t1, f.optional ? String.format("Optional<%s>", f.boxedType) : f.fullType, f.type.equals("Z") ? "is" : "get", f.uppercase, n);
                writer.printf("%sreturn %s;%s", t2, f.optional ? String.format("Optional.ofNullable(%s)", f.name) : f.name, n);
                writer.printf("%s}%s%2$s", t2, n);
                //value
                writer.printf("%spublic Immutable%s %s() {%s", t2, f.valueName, f.name, n);
                writer.printf("%sreturn %sSponge.getRegistry().getValueFactory().create%s(%s, %s).asImmutable();%s", t3, f.optional ? String.format("(ImmutableOptionalValue<%s>) ", f.boxedType) : "",f.valueType.getMutableName(), f.key.fullName, f.name, n);
                writer.printf("%s}%s%2$s", t2, n);
            });

            //asMutable()
            writer.printf("%s@Override%s%1$spublic %s asMutable() {%2$s", t2, n, classname);
            writer.printf("%sreturn new %s(%s);%s", t3, classname, argumentList, n);
            writer.printf("%s}%s%2$s", t2, n);

            //getContentVersion()
            writer.printf("%s@Override%s%1$spublic int getContentVersion() {%2$s", t2, n);
            writer.printf("%sreturn 1;%s", t3, n);
            writer.printf("%s}%s%2$s", t2, n);

            //toContainer()
            writer.printf("%s@Override%s%1$spublic DataContainer toContainer() {%2$s", t2, n);
            writer.printf("%sreturn super.toContainer()", t3);
            manipulator.fields.stream().filter(f -> !f.isTransient).forEach(
                    f -> writer.printf("%s%s.set(%s.getQuery(), %s)", n, t5, f.key.fullName, f.name));
            writer.printf(";%s%s}%1$s%1$s", n, t2);

            writer.printf("%s}%s%2$s", t1, n);

            //DataManipulatorBuilder

            //hello world
            writer.printf("%s@Generated(value = \"%s\", date = \"%s\")%s", t1, DataManipulatorGenerator.class.getName(), Instant.now(), n);

            //class header
            writer.printf("%spublic static class Builder extends AbstractDataBuilder<%s> implements DataManipulatorBuilder<%2$s, Immutable> {%s%3$s", t1, classname, n);

            //constructor
            writer.printf("%sprotected Builder() {%s%ssuper(%s.class, 1);%2$s%1$s}%2$s%2$s", t2, n, t3, classname);

            //create()
            writer.printf("%s@Override%s%1$spublic %s create() {%2$s%sreturn new %3$s();%2$s%1$s}%2$s%2$s", t2, n, classname, t3);

            //createFrom()
            writer.printf("%s@Override%s%1$spublic Optional<%s> createFrom(DataHolder dataHolder) {%2$s%sreturn create().fill(dataHolder);%2$s%1$s}%2$s%2$s", t2, n, classname, t3);

            //buildContent()
            writer.printf("%s@Override%s%1$sprotected Optional<%s> buildContent(DataView container) throws InvalidDataException {%2$s%sreturn create().from(container);%2$s%1$s}%2$s%2$s", t2, n, classname, t3);

            writer.printf("%s}%s}%2$s", t1, n);
            writer.flush();

            //keys file

            //package
            if (manipulator.packagename != null) {
                keys.printf("package %s;%s%2$s", manipulator.packagename, n);
            }

            //imports
            keyImports.stream().distinct().sorted().forEach(s -> keys.printf("import %s;%s", s, n));
            keys.println();

            //hello world
            keys.printf("@Generated(value = \"%s\", date = \"%s\")%s", DataManipulatorGenerator.class.getName(), Instant.now(), n);

            //class
            keys.printf("public class %s {%s%2$s", keyclass, n);

            //no constructor
            keys.printf("%sprivate %s() {}%s%3$s", t1, keyclass, n);

            //key definitions
            manipulator.fields.forEach(f -> keys.printf("%spublic final static Key<%s> %s;%s", t1, f.valueName, f.key.name, n));

            //key impls
            keys.printf("%sstatic {%s", t1, n);

            //TypeTokens
            manipulator.fields.stream().map(f -> new AbstractMap.SimpleImmutableEntry<>(f.key.itemType, f.key.valueType)).distinct().forEach(f -> {
                //class token
                keys.printf("%sTypeToken<%s> %sToken = %s;%s", t2, f.getKey(), strip(f.getKey()), String.format(f.getKey().contains("<") ? "new TypeToken<%s>(){}" : "TypeToken.of(%s.class)", f.getKey()), n);
                //value token
                keys.printf("%sTypeToken<%s> %sToken = new TypeToken<%2$s>(){};%s", t2, f.getValue(), strip(f.getValue()), n);
            });

            //key assignmemnt
            manipulator.fields.forEach(f -> keys.printf("%s%s = KeyFactory.make%sKey(%sToken, %sToken, DataQuery.of(%s\"%s\"), \"%s\", \"%s\");%s",
                    t2, f.key.name, f.valueType.getKeyType(), strip(f.key.itemType), strip(f.key.valueType),
                    f.key.dataQuery.contains(".")? "'.', ": "", f.key.dataQuery, f.key.id, f.key.displayName, n));

            keys.printf("%s}%s}%2$s", t1, n);

            keys.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    static String strip(String in) {
        return lowercase(in).replaceAll("\\W", "");
    }
    static String lowercase(String in) {
        return (Character.toLowerCase(in.charAt(0)) + in.substring(1)).replace("uUID", "uuid");
    }
    static String caps(String in) {
        return String.join("_", Arrays.stream(in.replaceAll("([a-z])([A-Z])", "$1.$2").split("[.]")).map(String::toUpperCase).collect(Collectors.toList()));
    }
    enum ValueType {
        MAP("MapValue", "ImmutableMapValue", "Map"),
        LIST("ListValue", "ImmutableListValue", "List"),
        SET("SetValue", "ImmutableSetValue", "Set"),
        OPTIONAL("OptionalValue", "ImmutableOptionalValue", "Optional"),
        REGULAR("Value", "ImmutableValue", "Single");
        String mutableName;
        String mutableType;
        String immutableName;
        String immutableType;
        String keyType;

        ValueType(String mutableName, String immutableName, String keyType) {
            this.mutableName = mutableName;
            this.mutableType = "org.spongepowered.api.data.value.mutable." + mutableName;
            this.immutableName = immutableName;
            this.immutableType = "org.spongepowered.api.data.value.immutable." + immutableName;
            this.keyType = keyType;
        }

        public String getMutableName() {
            return mutableName;
        }

        public String getMutableType() {
            return mutableType;
        }

        public String getImmutableName() {
            return immutableName;
        }

        public String getImmutableType() {
            return immutableType;
        }

        public String getKeyType() {
            return keyType;
        }
    }
}
