/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.function.fpml;

import java.time.Period;
import java.util.Set;
import java.util.logging.Logger;

import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.light.LightMetaBean;
import org.joda.convert.FromString;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.io.IniFile;
import com.opengamma.strata.collect.io.PropertySet;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.finance.rate.swap.OvernightAccrualMethod;

/**
 * Represents the FpML 'FloatingRateIndex' concept.
 * <p>
 * FpML provides a single key for floating rates of a variety of types.
 * The FpML value mixes Ibor, Overnight and Swap indices.
 * It also sometimes includes a source, such as 'Bloomberg' or 'Reuters'.
 * This class allows the value from FpML to be loaded and interpreted using a config INI file.
 * See {@code FpmlFloatingRateIndex.ini}.
 */
@BeanDefinition(style = "light")
final class FpmlFloatingRateIndex
    implements ImmutableBean {

  /**
   * The map of known instances.
   */
  static final ImmutableMap<String, FpmlFloatingRateIndex> DATA_MAP = loadIndices();

  /**
   * The extended enum lookup from name to instance.
   */
  public static enum Type {
    /** Ibor. */
    IBOR,
    /** Overnight compounded. */
    OVERNIGHT_COMPOUNDED,
    /** Overnight averaged. */
    OVERNIGHT_AVERAGED,
  };

  /**
   * The FpML name, such as 'GBP-LIBOR-BBA'.
   */
  @PropertyDefinition(validate = "notEmpty")
  private final String name;
  /**
   * The root of the name of the index, such as 'GBP-LIBOR-', to which the tenor is appended.
   */
  @PropertyDefinition(validate = "notEmpty")
  private final String indexName;
  /**
   * The type of the index.
   */
  @PropertyDefinition(validate = "notNull")
  private final Type type;

  //-------------------------------------------------------------------------
  /**
   * Obtains a {@code FpmlFloatingRateIndex} from a unique name.
   * 
   * @param name  the unique name
   * @return the index
   * @throws FpmlParseException if the name is not known
   */
  @FromString
  public static FpmlFloatingRateIndex of(String name) {
    ArgChecker.notNull(name, "uniqueName");
    FpmlFloatingRateIndex index = DATA_MAP.get(name);
    if (index == null) {
      throw new FpmlParseException("Unknown FpML Floating Rate Index: " + name);
    }
    return index;
  }

  //-------------------------------------------------------------------------
  /**
   * Loads the available indices.
   * 
   * @return the map of known indices
   */
  static ImmutableMap<String, FpmlFloatingRateIndex> loadIndices() {
    try {
      String name = FpmlFloatingRateIndex.class.getName().replace('.', '/') + ".ini";
      IniFile ini = IniFile.ofChained(ResourceLocator.streamOfClasspathResources(name).map(ResourceLocator::getCharSource));
      return parseIndices(ini);

    } catch (RuntimeException ex) {
      // logging used because this is loaded in a static variable
      Logger logger = Logger.getLogger(FpmlFloatingRateIndex.class.getName());
      logger.severe(Throwables.getStackTraceAsString(ex));
      // return an empty instance to avoid ExceptionInInitializerError
      return ImmutableMap.of();
    }
  }

  // parse the config file FpmlFloatingRateIndex.ini
  private static ImmutableMap<String, FpmlFloatingRateIndex> parseIndices(IniFile ini) {
    ImmutableMap.Builder<String, FpmlFloatingRateIndex> builder = ImmutableMap.builder();
    PropertySet iborSection = ini.section("ibor");
    for (String key : iborSection.keys()) {
      builder.put(key, new FpmlFloatingRateIndex(key, iborSection.value(key) + "-", Type.IBOR));
    }
    PropertySet onCompoundedSection = ini.section("overnightCompounded");
    for (String key : onCompoundedSection.keys()) {
      builder.put(key, new FpmlFloatingRateIndex(key, onCompoundedSection.value(key), Type.OVERNIGHT_COMPOUNDED));
    }
    PropertySet onAveragedSection = ini.section("overnightAveraged");
    for (String key : onAveragedSection.keys()) {
      builder.put(key, new FpmlFloatingRateIndex(key, onAveragedSection.value(key), Type.OVERNIGHT_AVERAGED));
    }
    return builder.build();
  }

  //-------------------------------------------------------------------------
  /**
   * Converts to an {@link IborIndex}.
   * 
   * @param period  the tenor of the index
   * @return the index
   */
  public IborIndex toIborIndex(Period period) {
    if (type != Type.IBOR) {
      throw new FpmlParseException("Incorrect index type, expected Ibor: " + name);
    }
    return IborIndex.of(indexName + period.toString().substring(1));
  }

  /**
   * Converts to an {@link OvernightIndex}.
   * 
   * @return the index
   */
  public OvernightIndex toOvernightIndex() {
    if (type != Type.OVERNIGHT_AVERAGED && type != Type.OVERNIGHT_COMPOUNDED) {
      throw new FpmlParseException("Incorrect index type, expected Overnight: " + name);
    }
    return OvernightIndex.of(indexName);
  }

  /**
   * Converts to an {@link OvernightIndex}.
   * 
   * @return the method
   */
  public OvernightAccrualMethod toOvernightAccrualMethod() {
    switch (type) {
      case OVERNIGHT_AVERAGED:
        return OvernightAccrualMethod.AVERAGED;
      case OVERNIGHT_COMPOUNDED:
        return OvernightAccrualMethod.COMPOUNDED;
      default:
      throw new FpmlParseException("Incorrect index type, expected Overnight: " + name);
    }
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code FpmlFloatingRateIndex}.
   */
  private static MetaBean META_BEAN = LightMetaBean.of(FpmlFloatingRateIndex.class);

  /**
   * The meta-bean for {@code FpmlFloatingRateIndex}.
   * @return the meta-bean, not null
   */
  public static MetaBean meta() {
    return META_BEAN;
  }

  private FpmlFloatingRateIndex(
      String name,
      String indexName,
      Type type) {
    JodaBeanUtils.notEmpty(name, "name");
    JodaBeanUtils.notEmpty(indexName, "indexName");
    JodaBeanUtils.notNull(type, "type");
    this.name = name;
    this.indexName = indexName;
    this.type = type;
  }

  @Override
  public MetaBean metaBean() {
    return META_BEAN;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the FpML name, such as 'GBP-LIBOR-BBA'.
   * @return the value of the property, not empty
   */
  public String getName() {
    return name;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the root of the name of the index, such as 'GBP-LIBOR-', to which the tenor is appended.
   * @return the value of the property, not empty
   */
  public String getIndexName() {
    return indexName;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the type of the index.
   * @return the value of the property, not null
   */
  public Type getType() {
    return type;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      FpmlFloatingRateIndex other = (FpmlFloatingRateIndex) obj;
      return JodaBeanUtils.equal(getName(), other.getName()) &&
          JodaBeanUtils.equal(getIndexName(), other.getIndexName()) &&
          JodaBeanUtils.equal(getType(), other.getType());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getName());
    hash = hash * 31 + JodaBeanUtils.hashCode(getIndexName());
    hash = hash * 31 + JodaBeanUtils.hashCode(getType());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(128);
    buf.append("FpmlFloatingRateIndex{");
    buf.append("name").append('=').append(getName()).append(',').append(' ');
    buf.append("indexName").append('=').append(getIndexName()).append(',').append(' ');
    buf.append("type").append('=').append(JodaBeanUtils.toString(getType()));
    buf.append('}');
    return buf.toString();
  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
