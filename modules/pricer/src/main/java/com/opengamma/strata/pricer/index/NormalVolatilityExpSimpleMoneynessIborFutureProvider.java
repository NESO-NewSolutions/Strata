/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.index;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.tuple.DoublesPair;
import com.opengamma.strata.market.sensitivity.IborFutureOptionSensitivity;
import com.opengamma.strata.market.surface.InterpolatedNodalSurface;

/**
 * Data provider of volatility for Ibor future options in the normal or Bachelier model. 
 * <p>
 * The volatility is represented by a surface on the expiry and simple moneyness. 
 * The expiry is measured in number of days (not time) according to a day-count convention.
 * The simple moneyness can be on the price or on the rate (1-price).
 */
@BeanDefinition
public final class NormalVolatilityExpSimpleMoneynessIborFutureProvider
    implements NormalVolatilityIborFutureProvider, ImmutableBean {

  /**
   * The normal volatility surface.
   * The order of the dimensions is expiry/simple moneyness.
   */
  @PropertyDefinition(validate = "notNull")
  private final InterpolatedNodalSurface parameters;
  /**
   * Flag indicating if the moneyness is on the price (true) or on the rate (false).
   */
  @PropertyDefinition
  private final boolean isMoneynessOnPrice;
  /**
   * The Ibor index of the underlying future.
   */
  @PropertyDefinition(validate = "notNull")
  private final IborIndex index;
  /**
   * The day count applicable to the model.
   */
  @PropertyDefinition(validate = "notNull")
  private final DayCount dayCount;
  /**
   * The valuation date-time.
   * All data items in this provider is calibrated for this date-time.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final ZonedDateTime valuationDateTime;

  //-------------------------------------------------------------------------
  /**
   * Obtains an instance based on a surface.
   * 
   * @param surface  the normal volatility surface
   * @param isMoneynessOnPrice  flag indicating if the moneyness is on the price (true) or on the rate (false)
   * @param index  the Ibor index of the underlying future
   * @param dayCount  the day count applicable to the model
   * @param valuationTime  the valuation date-time
   * @return the provider
   */
  public static NormalVolatilityExpSimpleMoneynessIborFutureProvider of(
      InterpolatedNodalSurface surface,
      boolean isMoneynessOnPrice,
      IborIndex index,
      DayCount dayCount,
      ZonedDateTime valuationTime) {

    return new NormalVolatilityExpSimpleMoneynessIborFutureProvider(
        surface, isMoneynessOnPrice, index, dayCount, valuationTime);
  }

  //-------------------------------------------------------------------------
  @Override
  public double getVolatility(ZonedDateTime expiry, LocalDate fixingDate, double strikePrice, double futurePrice) {
    double simpleMoneyness = isMoneynessOnPrice ? strikePrice - futurePrice : futurePrice - strikePrice;
    double expiryTime = relativeTime(expiry);
    return parameters.zValue(expiryTime, simpleMoneyness);
  }

  @Override
  public IborIndex getFutureIndex() {
    return index;
  }

  //-------------------------------------------------------------------------
  @Override
  public double relativeTime(ZonedDateTime zonedDateTime) {
    ArgChecker.notNull(zonedDateTime, "date");
    return dayCount.relativeYearFraction(valuationDateTime.toLocalDate(), zonedDateTime.toLocalDate());
  }

  /**
   * Computes the sensitivity to the nodes used in the description of the normal volatility
   * from a point sensitivity.
   * 
   * @param point  the point sensitivity at a given key
   * @return the sensitivity to the surface nodes
   */
  public Map<DoublesPair, Double> nodeSensitivity(IborFutureOptionSensitivity point) {
    double simpleMoneyness = isMoneynessOnPrice ?
        point.getStrikePrice() - point.getFuturePrice() : point.getFuturePrice() - point.getStrikePrice();
    double expiryTime = relativeTime(point.getExpiry());
    Map<DoublesPair, Double> ns = parameters.zValueParameterSensitivity(expiryTime, simpleMoneyness);
    Map<DoublesPair, Double> result = new HashMap<>();
    for (Entry<DoublesPair, Double> entry : ns.entrySet()) {
      result.put(entry.getKey(), entry.getValue() * point.getSensitivity());
    }
    return result;
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code NormalVolatilityExpSimpleMoneynessIborFutureProvider}.
   * @return the meta-bean, not null
   */
  public static NormalVolatilityExpSimpleMoneynessIborFutureProvider.Meta meta() {
    return NormalVolatilityExpSimpleMoneynessIborFutureProvider.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(NormalVolatilityExpSimpleMoneynessIborFutureProvider.Meta.INSTANCE);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static NormalVolatilityExpSimpleMoneynessIborFutureProvider.Builder builder() {
    return new NormalVolatilityExpSimpleMoneynessIborFutureProvider.Builder();
  }

  private NormalVolatilityExpSimpleMoneynessIborFutureProvider(
      InterpolatedNodalSurface parameters,
      boolean isMoneynessOnPrice,
      IborIndex index,
      DayCount dayCount,
      ZonedDateTime valuationDateTime) {
    JodaBeanUtils.notNull(parameters, "parameters");
    JodaBeanUtils.notNull(index, "index");
    JodaBeanUtils.notNull(dayCount, "dayCount");
    JodaBeanUtils.notNull(valuationDateTime, "valuationDateTime");
    this.parameters = parameters;
    this.isMoneynessOnPrice = isMoneynessOnPrice;
    this.index = index;
    this.dayCount = dayCount;
    this.valuationDateTime = valuationDateTime;
  }

  @Override
  public NormalVolatilityExpSimpleMoneynessIborFutureProvider.Meta metaBean() {
    return NormalVolatilityExpSimpleMoneynessIborFutureProvider.Meta.INSTANCE;
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
   * Gets the normal volatility surface.
   * The order of the dimensions is expiry/simple moneyness.
   * @return the value of the property, not null
   */
  public InterpolatedNodalSurface getParameters() {
    return parameters;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets flag indicating if the moneyness is on the price (true) or on the rate (false).
   * @return the value of the property
   */
  public boolean isIsMoneynessOnPrice() {
    return isMoneynessOnPrice;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the Ibor index of the underlying future.
   * @return the value of the property, not null
   */
  public IborIndex getIndex() {
    return index;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the day count applicable to the model.
   * @return the value of the property, not null
   */
  public DayCount getDayCount() {
    return dayCount;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the valuation date-time.
   * All data items in this provider is calibrated for this date-time.
   * @return the value of the property, not null
   */
  @Override
  public ZonedDateTime getValuationDateTime() {
    return valuationDateTime;
  }

  //-----------------------------------------------------------------------
  /**
   * Returns a builder that allows this bean to be mutated.
   * @return the mutable builder, not null
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      NormalVolatilityExpSimpleMoneynessIborFutureProvider other = (NormalVolatilityExpSimpleMoneynessIborFutureProvider) obj;
      return JodaBeanUtils.equal(parameters, other.parameters) &&
          (isMoneynessOnPrice == other.isMoneynessOnPrice) &&
          JodaBeanUtils.equal(index, other.index) &&
          JodaBeanUtils.equal(dayCount, other.dayCount) &&
          JodaBeanUtils.equal(valuationDateTime, other.valuationDateTime);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(parameters);
    hash = hash * 31 + JodaBeanUtils.hashCode(isMoneynessOnPrice);
    hash = hash * 31 + JodaBeanUtils.hashCode(index);
    hash = hash * 31 + JodaBeanUtils.hashCode(dayCount);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDateTime);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(192);
    buf.append("NormalVolatilityExpSimpleMoneynessIborFutureProvider{");
    buf.append("parameters").append('=').append(parameters).append(',').append(' ');
    buf.append("isMoneynessOnPrice").append('=').append(isMoneynessOnPrice).append(',').append(' ');
    buf.append("index").append('=').append(index).append(',').append(' ');
    buf.append("dayCount").append('=').append(dayCount).append(',').append(' ');
    buf.append("valuationDateTime").append('=').append(JodaBeanUtils.toString(valuationDateTime));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code NormalVolatilityExpSimpleMoneynessIborFutureProvider}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code parameters} property.
     */
    private final MetaProperty<InterpolatedNodalSurface> parameters = DirectMetaProperty.ofImmutable(
        this, "parameters", NormalVolatilityExpSimpleMoneynessIborFutureProvider.class, InterpolatedNodalSurface.class);
    /**
     * The meta-property for the {@code isMoneynessOnPrice} property.
     */
    private final MetaProperty<Boolean> isMoneynessOnPrice = DirectMetaProperty.ofImmutable(
        this, "isMoneynessOnPrice", NormalVolatilityExpSimpleMoneynessIborFutureProvider.class, Boolean.TYPE);
    /**
     * The meta-property for the {@code index} property.
     */
    private final MetaProperty<IborIndex> index = DirectMetaProperty.ofImmutable(
        this, "index", NormalVolatilityExpSimpleMoneynessIborFutureProvider.class, IborIndex.class);
    /**
     * The meta-property for the {@code dayCount} property.
     */
    private final MetaProperty<DayCount> dayCount = DirectMetaProperty.ofImmutable(
        this, "dayCount", NormalVolatilityExpSimpleMoneynessIborFutureProvider.class, DayCount.class);
    /**
     * The meta-property for the {@code valuationDateTime} property.
     */
    private final MetaProperty<ZonedDateTime> valuationDateTime = DirectMetaProperty.ofImmutable(
        this, "valuationDateTime", NormalVolatilityExpSimpleMoneynessIborFutureProvider.class, ZonedDateTime.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "parameters",
        "isMoneynessOnPrice",
        "index",
        "dayCount",
        "valuationDateTime");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 458736106:  // parameters
          return parameters;
        case 681457885:  // isMoneynessOnPrice
          return isMoneynessOnPrice;
        case 100346066:  // index
          return index;
        case 1905311443:  // dayCount
          return dayCount;
        case -949589828:  // valuationDateTime
          return valuationDateTime;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public NormalVolatilityExpSimpleMoneynessIborFutureProvider.Builder builder() {
      return new NormalVolatilityExpSimpleMoneynessIborFutureProvider.Builder();
    }

    @Override
    public Class<? extends NormalVolatilityExpSimpleMoneynessIborFutureProvider> beanType() {
      return NormalVolatilityExpSimpleMoneynessIborFutureProvider.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code parameters} property.
     * @return the meta-property, not null
     */
    public MetaProperty<InterpolatedNodalSurface> parameters() {
      return parameters;
    }

    /**
     * The meta-property for the {@code isMoneynessOnPrice} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Boolean> isMoneynessOnPrice() {
      return isMoneynessOnPrice;
    }

    /**
     * The meta-property for the {@code index} property.
     * @return the meta-property, not null
     */
    public MetaProperty<IborIndex> index() {
      return index;
    }

    /**
     * The meta-property for the {@code dayCount} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DayCount> dayCount() {
      return dayCount;
    }

    /**
     * The meta-property for the {@code valuationDateTime} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ZonedDateTime> valuationDateTime() {
      return valuationDateTime;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 458736106:  // parameters
          return ((NormalVolatilityExpSimpleMoneynessIborFutureProvider) bean).getParameters();
        case 681457885:  // isMoneynessOnPrice
          return ((NormalVolatilityExpSimpleMoneynessIborFutureProvider) bean).isIsMoneynessOnPrice();
        case 100346066:  // index
          return ((NormalVolatilityExpSimpleMoneynessIborFutureProvider) bean).getIndex();
        case 1905311443:  // dayCount
          return ((NormalVolatilityExpSimpleMoneynessIborFutureProvider) bean).getDayCount();
        case -949589828:  // valuationDateTime
          return ((NormalVolatilityExpSimpleMoneynessIborFutureProvider) bean).getValuationDateTime();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code NormalVolatilityExpSimpleMoneynessIborFutureProvider}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<NormalVolatilityExpSimpleMoneynessIborFutureProvider> {

    private InterpolatedNodalSurface parameters;
    private boolean isMoneynessOnPrice;
    private IborIndex index;
    private DayCount dayCount;
    private ZonedDateTime valuationDateTime;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(NormalVolatilityExpSimpleMoneynessIborFutureProvider beanToCopy) {
      this.parameters = beanToCopy.getParameters();
      this.isMoneynessOnPrice = beanToCopy.isIsMoneynessOnPrice();
      this.index = beanToCopy.getIndex();
      this.dayCount = beanToCopy.getDayCount();
      this.valuationDateTime = beanToCopy.getValuationDateTime();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 458736106:  // parameters
          return parameters;
        case 681457885:  // isMoneynessOnPrice
          return isMoneynessOnPrice;
        case 100346066:  // index
          return index;
        case 1905311443:  // dayCount
          return dayCount;
        case -949589828:  // valuationDateTime
          return valuationDateTime;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 458736106:  // parameters
          this.parameters = (InterpolatedNodalSurface) newValue;
          break;
        case 681457885:  // isMoneynessOnPrice
          this.isMoneynessOnPrice = (Boolean) newValue;
          break;
        case 100346066:  // index
          this.index = (IborIndex) newValue;
          break;
        case 1905311443:  // dayCount
          this.dayCount = (DayCount) newValue;
          break;
        case -949589828:  // valuationDateTime
          this.valuationDateTime = (ZonedDateTime) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public NormalVolatilityExpSimpleMoneynessIborFutureProvider build() {
      return new NormalVolatilityExpSimpleMoneynessIborFutureProvider(
          parameters,
          isMoneynessOnPrice,
          index,
          dayCount,
          valuationDateTime);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the normal volatility surface.
     * The order of the dimensions is expiry/simple moneyness.
     * @param parameters  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder parameters(InterpolatedNodalSurface parameters) {
      JodaBeanUtils.notNull(parameters, "parameters");
      this.parameters = parameters;
      return this;
    }

    /**
     * Sets flag indicating if the moneyness is on the price (true) or on the rate (false).
     * @param isMoneynessOnPrice  the new value
     * @return this, for chaining, not null
     */
    public Builder isMoneynessOnPrice(boolean isMoneynessOnPrice) {
      this.isMoneynessOnPrice = isMoneynessOnPrice;
      return this;
    }

    /**
     * Sets the Ibor index of the underlying future.
     * @param index  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder index(IborIndex index) {
      JodaBeanUtils.notNull(index, "index");
      this.index = index;
      return this;
    }

    /**
     * Sets the day count applicable to the model.
     * @param dayCount  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dayCount(DayCount dayCount) {
      JodaBeanUtils.notNull(dayCount, "dayCount");
      this.dayCount = dayCount;
      return this;
    }

    /**
     * Sets the valuation date-time.
     * All data items in this provider is calibrated for this date-time.
     * @param valuationDateTime  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder valuationDateTime(ZonedDateTime valuationDateTime) {
      JodaBeanUtils.notNull(valuationDateTime, "valuationDateTime");
      this.valuationDateTime = valuationDateTime;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(192);
      buf.append("NormalVolatilityExpSimpleMoneynessIborFutureProvider.Builder{");
      buf.append("parameters").append('=').append(JodaBeanUtils.toString(parameters)).append(',').append(' ');
      buf.append("isMoneynessOnPrice").append('=').append(JodaBeanUtils.toString(isMoneynessOnPrice)).append(',').append(' ');
      buf.append("index").append('=').append(JodaBeanUtils.toString(index)).append(',').append(' ');
      buf.append("dayCount").append('=').append(JodaBeanUtils.toString(dayCount)).append(',').append(' ');
      buf.append("valuationDateTime").append('=').append(JodaBeanUtils.toString(valuationDateTime));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
