/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.bond;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import com.google.common.primitives.Doubles;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.collect.tuple.DoublesPair;
import com.opengamma.strata.market.option.LogMoneynessStrike;
import com.opengamma.strata.market.sensitivity.BondFutureOptionSensitivity;
import com.opengamma.strata.market.surface.InterpolatedNodalSurface;
import com.opengamma.strata.market.surface.NodalSurface;
import com.opengamma.strata.market.surface.SurfaceCurrencyParameterSensitivity;
import com.opengamma.strata.market.surface.SurfaceMetadata;
import com.opengamma.strata.market.surface.SurfaceParameterMetadata;
import com.opengamma.strata.market.surface.meta.GenericVolatilitySurfaceYearFractionMetadata;

/**
 * Data provider of volatility for bond future options in the log-normal or Black model. 
 * <p>
 * The volatility is represented by a surface on the expiry and log moneyness. 
 * The expiry is measured in number of days (not time) according to a day-count convention.
 */
@BeanDefinition
public final class BlackVolatilityExpLogMoneynessBondFutureProvider
    implements BlackVolatilityBondFutureProvider, ImmutableBean {

  /**
   * The log-normal volatility surface.
   * The order of the dimensions is expiry/log moneyness.
   */
  @PropertyDefinition(validate = "notNull")
  private final NodalSurface parameters;
  /**
   * The ID of the underlying future.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final StandardId futureSecurityId;
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
   * @param surface  the Black volatility surface
   * @param futureSecurityId  the ID of the underlying future
   * @param dayCount  the day count applicable to the model
   * @param valuationTime  the valuation date-time
   * @return the provider
   */
  public static BlackVolatilityExpLogMoneynessBondFutureProvider of(
      InterpolatedNodalSurface surface,
      StandardId futureSecurityId,
      DayCount dayCount,
      ZonedDateTime valuationTime) {

    return new BlackVolatilityExpLogMoneynessBondFutureProvider(
        surface, futureSecurityId, dayCount, valuationTime);
  }

  //-------------------------------------------------------------------------
  @Override
  public double getVolatility(ZonedDateTime expiry, LocalDate fixingDate, double strikePrice, double futurePrice) {
    ArgChecker.notNegativeOrZero(strikePrice, "strikePrice");
    ArgChecker.notNegativeOrZero(futurePrice, "futurePrice");
    double logMoneyness = Math.log(strikePrice / futurePrice);
    double expiryTime = relativeTime(expiry);
    return parameters.zValue(expiryTime, logMoneyness);
  }

  //-------------------------------------------------------------------------
  @Override
  public double relativeTime(ZonedDateTime zonedDateTime) {
    ArgChecker.notNull(zonedDateTime, "date");
    return dayCount.relativeYearFraction(valuationDateTime.toLocalDate(), zonedDateTime.toLocalDate());
  }

  /** 
   * Computes the sensitivity to the surface parameter used in the description of the black volatility
   * from a point sensitivity.
   * 
   * @param point  the point sensitivity at a given key
   * @return the sensitivity to the surface nodes
   */
  public SurfaceCurrencyParameterSensitivity surfaceCurrencyParameterSensitivity(BondFutureOptionSensitivity point) {
    double logMoneyness = Math.log(point.getStrikePrice() / point.getFuturePrice());
    double expiryTime = relativeTime(point.getExpiry());
    Map<DoublesPair, Double> result = parameters.zValueParameterSensitivity(expiryTime, logMoneyness);
    SurfaceCurrencyParameterSensitivity parameterSensi = SurfaceCurrencyParameterSensitivity.of(
        updateSurfaceMetadata(parameters.getMetadata(), result.keySet()), point.getCurrency(),
        DoubleArray.copyOf(Doubles.toArray(result.values())));
    return parameterSensi.multipliedBy(point.getSensitivity());
  }

  private SurfaceMetadata updateSurfaceMetadata(SurfaceMetadata surfaceMetadata, Set<DoublesPair> pairs) {
    List<SurfaceParameterMetadata> sortedMetaList = new ArrayList<SurfaceParameterMetadata>();
    if (surfaceMetadata.getParameterMetadata().isPresent()) {
      List<SurfaceParameterMetadata> metaList =
          new ArrayList<SurfaceParameterMetadata>(surfaceMetadata.getParameterMetadata().get());
      for (DoublesPair pair : pairs) {
        metadataLoop:
        for (SurfaceParameterMetadata parameterMetadata : metaList) {
          ArgChecker.isTrue(parameterMetadata instanceof GenericVolatilitySurfaceYearFractionMetadata,
              "surface parameter metadata must be instance of GenericVolatilitySurfaceYearFractionMetadata");
          GenericVolatilitySurfaceYearFractionMetadata casted =
              (GenericVolatilitySurfaceYearFractionMetadata) parameterMetadata;
          if (pair.getFirst() == casted.getYearFraction() && pair.getSecond() == casted.getStrike().getValue()) {
            sortedMetaList.add(casted);
            metaList.remove(parameterMetadata);
            break metadataLoop;
          }
        }
      }
      ArgChecker.isTrue(metaList.size() == 0, "mismatch between surface parameter metadata list and doubles pair list");
    } else {
      for (DoublesPair pair : pairs) {
        GenericVolatilitySurfaceYearFractionMetadata parameterMetadata =
            GenericVolatilitySurfaceYearFractionMetadata.of(pair.getFirst(), LogMoneynessStrike.of(pair.getSecond()));
        sortedMetaList.add(parameterMetadata);
      }
    }
    return surfaceMetadata.withParameterMetadata(sortedMetaList);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code BlackVolatilityExpLogMoneynessBondFutureProvider}.
   * @return the meta-bean, not null
   */
  public static BlackVolatilityExpLogMoneynessBondFutureProvider.Meta meta() {
    return BlackVolatilityExpLogMoneynessBondFutureProvider.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(BlackVolatilityExpLogMoneynessBondFutureProvider.Meta.INSTANCE);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static BlackVolatilityExpLogMoneynessBondFutureProvider.Builder builder() {
    return new BlackVolatilityExpLogMoneynessBondFutureProvider.Builder();
  }

  private BlackVolatilityExpLogMoneynessBondFutureProvider(
      NodalSurface parameters,
      StandardId futureSecurityId,
      DayCount dayCount,
      ZonedDateTime valuationDateTime) {
    JodaBeanUtils.notNull(parameters, "parameters");
    JodaBeanUtils.notNull(futureSecurityId, "futureSecurityId");
    JodaBeanUtils.notNull(dayCount, "dayCount");
    JodaBeanUtils.notNull(valuationDateTime, "valuationDateTime");
    this.parameters = parameters;
    this.futureSecurityId = futureSecurityId;
    this.dayCount = dayCount;
    this.valuationDateTime = valuationDateTime;
  }

  @Override
  public BlackVolatilityExpLogMoneynessBondFutureProvider.Meta metaBean() {
    return BlackVolatilityExpLogMoneynessBondFutureProvider.Meta.INSTANCE;
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
   * Gets the log-normal volatility surface.
   * The order of the dimensions is expiry/log moneyness.
   * @return the value of the property, not null
   */
  public NodalSurface getParameters() {
    return parameters;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the ID of the underlying future.
   * @return the value of the property, not null
   */
  @Override
  public StandardId getFutureSecurityId() {
    return futureSecurityId;
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
      BlackVolatilityExpLogMoneynessBondFutureProvider other = (BlackVolatilityExpLogMoneynessBondFutureProvider) obj;
      return JodaBeanUtils.equal(parameters, other.parameters) &&
          JodaBeanUtils.equal(futureSecurityId, other.futureSecurityId) &&
          JodaBeanUtils.equal(dayCount, other.dayCount) &&
          JodaBeanUtils.equal(valuationDateTime, other.valuationDateTime);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(parameters);
    hash = hash * 31 + JodaBeanUtils.hashCode(futureSecurityId);
    hash = hash * 31 + JodaBeanUtils.hashCode(dayCount);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDateTime);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(160);
    buf.append("BlackVolatilityExpLogMoneynessBondFutureProvider{");
    buf.append("parameters").append('=').append(parameters).append(',').append(' ');
    buf.append("futureSecurityId").append('=').append(futureSecurityId).append(',').append(' ');
    buf.append("dayCount").append('=').append(dayCount).append(',').append(' ');
    buf.append("valuationDateTime").append('=').append(JodaBeanUtils.toString(valuationDateTime));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code BlackVolatilityExpLogMoneynessBondFutureProvider}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code parameters} property.
     */
    private final MetaProperty<NodalSurface> parameters = DirectMetaProperty.ofImmutable(
        this, "parameters", BlackVolatilityExpLogMoneynessBondFutureProvider.class, NodalSurface.class);
    /**
     * The meta-property for the {@code futureSecurityId} property.
     */
    private final MetaProperty<StandardId> futureSecurityId = DirectMetaProperty.ofImmutable(
        this, "futureSecurityId", BlackVolatilityExpLogMoneynessBondFutureProvider.class, StandardId.class);
    /**
     * The meta-property for the {@code dayCount} property.
     */
    private final MetaProperty<DayCount> dayCount = DirectMetaProperty.ofImmutable(
        this, "dayCount", BlackVolatilityExpLogMoneynessBondFutureProvider.class, DayCount.class);
    /**
     * The meta-property for the {@code valuationDateTime} property.
     */
    private final MetaProperty<ZonedDateTime> valuationDateTime = DirectMetaProperty.ofImmutable(
        this, "valuationDateTime", BlackVolatilityExpLogMoneynessBondFutureProvider.class, ZonedDateTime.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "parameters",
        "futureSecurityId",
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
        case 1270940318:  // futureSecurityId
          return futureSecurityId;
        case 1905311443:  // dayCount
          return dayCount;
        case -949589828:  // valuationDateTime
          return valuationDateTime;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BlackVolatilityExpLogMoneynessBondFutureProvider.Builder builder() {
      return new BlackVolatilityExpLogMoneynessBondFutureProvider.Builder();
    }

    @Override
    public Class<? extends BlackVolatilityExpLogMoneynessBondFutureProvider> beanType() {
      return BlackVolatilityExpLogMoneynessBondFutureProvider.class;
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
    public MetaProperty<NodalSurface> parameters() {
      return parameters;
    }

    /**
     * The meta-property for the {@code futureSecurityId} property.
     * @return the meta-property, not null
     */
    public MetaProperty<StandardId> futureSecurityId() {
      return futureSecurityId;
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
          return ((BlackVolatilityExpLogMoneynessBondFutureProvider) bean).getParameters();
        case 1270940318:  // futureSecurityId
          return ((BlackVolatilityExpLogMoneynessBondFutureProvider) bean).getFutureSecurityId();
        case 1905311443:  // dayCount
          return ((BlackVolatilityExpLogMoneynessBondFutureProvider) bean).getDayCount();
        case -949589828:  // valuationDateTime
          return ((BlackVolatilityExpLogMoneynessBondFutureProvider) bean).getValuationDateTime();
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
   * The bean-builder for {@code BlackVolatilityExpLogMoneynessBondFutureProvider}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<BlackVolatilityExpLogMoneynessBondFutureProvider> {

    private NodalSurface parameters;
    private StandardId futureSecurityId;
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
    private Builder(BlackVolatilityExpLogMoneynessBondFutureProvider beanToCopy) {
      this.parameters = beanToCopy.getParameters();
      this.futureSecurityId = beanToCopy.getFutureSecurityId();
      this.dayCount = beanToCopy.getDayCount();
      this.valuationDateTime = beanToCopy.getValuationDateTime();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 458736106:  // parameters
          return parameters;
        case 1270940318:  // futureSecurityId
          return futureSecurityId;
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
          this.parameters = (NodalSurface) newValue;
          break;
        case 1270940318:  // futureSecurityId
          this.futureSecurityId = (StandardId) newValue;
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
    public BlackVolatilityExpLogMoneynessBondFutureProvider build() {
      return new BlackVolatilityExpLogMoneynessBondFutureProvider(
          parameters,
          futureSecurityId,
          dayCount,
          valuationDateTime);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the log-normal volatility surface.
     * The order of the dimensions is expiry/log moneyness.
     * @param parameters  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder parameters(NodalSurface parameters) {
      JodaBeanUtils.notNull(parameters, "parameters");
      this.parameters = parameters;
      return this;
    }

    /**
     * Sets the ID of the underlying future.
     * @param futureSecurityId  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder futureSecurityId(StandardId futureSecurityId) {
      JodaBeanUtils.notNull(futureSecurityId, "futureSecurityId");
      this.futureSecurityId = futureSecurityId;
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
      StringBuilder buf = new StringBuilder(160);
      buf.append("BlackVolatilityExpLogMoneynessBondFutureProvider.Builder{");
      buf.append("parameters").append('=').append(JodaBeanUtils.toString(parameters)).append(',').append(' ');
      buf.append("futureSecurityId").append('=').append(JodaBeanUtils.toString(futureSecurityId)).append(',').append(' ');
      buf.append("dayCount").append('=').append(JodaBeanUtils.toString(dayCount)).append(',').append(' ');
      buf.append("valuationDateTime").append('=').append(JodaBeanUtils.toString(valuationDateTime));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
