package org.snakeyaml.custom_config;

import java.util.Map;

/**
 * Represents a source of configurable options and Settings.INSTANCE
 */
public interface Configuration extends ConfigurationSection {

    /**
     * Sets the default value of the given path as provided.
     *
     * <p>
     * If no source {@link Configuration} was provided as a default collection, then a new {@link
     * MemoryConfiguration} will be created to hold the new default value.
     * </p>
     *
     * <p>
     * If value is null, the value will be removed from the default Configuration source.
     * </p>
     *
     * @param path  Path of the value to set.
     * @param value Value to set the default to.
     * @throws IllegalArgumentException Thrown if path is null.
     */
    @Override
    void addDefault(final String path, final Object value);

    /**
     * Sets the default values of the given paths as provided.
     *
     * <p>
     * If no source {@link Configuration} was provided as a default collection, then a new {@link
     * MemoryConfiguration} will be created to hold the new default values.
     * </p>
     *
     * @param defaults A map of Path->Values to add to defaults.
     * @throws IllegalArgumentException Thrown if defaults is null.
     */
    void addDefaults(final Map<String, Object> defaults);

    /**
     * Sets the default values of the given paths as provided.
     *
     * <p>
     * If no source {@link Configuration} was provided as a default collection, then a new {@link
     * MemoryConfiguration} will be created to hold the new default value.
     * </p>
     *
     * <p>
     * This method will not hold a reference to the specified Configuration, nor will it
     * automatically update if that Configuration ever changes. If you require this, you should set
     * the default source with {@link #setDefaults(Configuration)}.
     * </p>
     *
     * @param defaults A configuration holding a list of defaults to copy.
     * @throws IllegalArgumentException Thrown if defaults is null or this.
     */
    void addDefaults(final Configuration defaults);

    /**
     * Sets the source of all default values for this {@link Configuration}.
     *
     * <p>
     * If a previous source was set, or previous default values were defined, then they will not be
     * copied to the new source.
     * </p>
     *
     * @param defaults New source of default values for this configuration.
     * @throws IllegalArgumentException Thrown if defaults is null or this.
     */
    void setDefaults(final Configuration defaults);

    /**
     * Gets the source {@link Configuration} for this configuration.
     *
     * <p>
     * If no configuration source was set, but default values were added, then a {@link
     * MemoryConfiguration} will be returned. If no source was set and no defaults were set, then
     * this method will return null.
     * </p>
     *
     * @return Configuration source for default values, or null if none exist.
     */
    Configuration getDefaults();

    /**
     * Gets the {@link ConfigurationOptions} for this {@link Configuration}.
     *
     * <p>
     * All setters through this method are chainable.
     * </p>
     *
     * @return Options for this configuration
     */
    ConfigurationOptions options();

}
