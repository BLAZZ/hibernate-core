/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.source.annotations.entity;

import javax.persistence.AccessType;
import javax.persistence.DiscriminatorType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import org.hibernate.AnnotationException;
import org.hibernate.MappingException;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.OptimisticLockType;
import org.hibernate.annotations.PolymorphismType;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.engine.spi.ExecuteUpdateResultCheckStyle;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.metamodel.binding.Caching;
import org.hibernate.metamodel.binding.CustomSQL;
import org.hibernate.metamodel.binding.InheritanceType;
import org.hibernate.metamodel.source.annotations.AnnotationBindingContext;
import org.hibernate.metamodel.source.annotations.HibernateDotNames;
import org.hibernate.metamodel.source.annotations.JPADotNames;
import org.hibernate.metamodel.source.annotations.JandexHelper;
import org.hibernate.metamodel.source.annotations.attribute.ColumnValues;
import org.hibernate.metamodel.source.annotations.attribute.FormulaValue;
import org.hibernate.metamodel.source.binder.ConstraintSource;
import org.hibernate.metamodel.source.binder.TableSource;

/**
 * Represents an entity or mapped superclass configured via annotations/orm-xml.
 *
 * @author Hardy Ferentschik
 */
public class EntityClass extends ConfiguredClass {
	private final IdType idType;
	private final InheritanceType inheritanceType;

	private final String explicitEntityName;
	private final String customLoaderQueryName;
	private final List<String> synchronizedTableNames;
	private final int batchSize;

	private final TableSource primaryTableSource;
	private final Set<TableSource> secondaryTableSources;
	private final Set<ConstraintSource> constraintSources;

	private boolean isMutable;
	private boolean isExplicitPolymorphism;
	private OptimisticLockStyle optimisticLockStyle;
	private String whereClause;
	private String rowId;
	private Caching caching;
	private boolean isDynamicInsert;
	private boolean isDynamicUpdate;
	private boolean isSelectBeforeUpdate;
	private String customPersister;

	private CustomSQL customInsert;
	private CustomSQL customUpdate;
	private CustomSQL customDelete;

	private boolean isLazy;
	private String proxy;

	private ColumnValues discriminatorColumnValues;
    private FormulaValue discriminatorFormula;
	private Class<?> discriminatorType;
	private String discriminatorMatchValue;
	private boolean isDiscriminatorForced = true;
	private boolean isDiscriminatorIncludedInSql = true;


	public EntityClass(
			ClassInfo classInfo,
			EntityClass parent,
			AccessType hierarchyAccessType,
			InheritanceType inheritanceType,
			AnnotationBindingContext context) {
		super( classInfo, hierarchyAccessType, parent, context );
		this.inheritanceType = inheritanceType;
		this.idType = determineIdType();
		boolean hasOwnTable = definesItsOwnTable();
		this.explicitEntityName = determineExplicitEntityName();
		this.constraintSources = new HashSet<ConstraintSource>();

		if ( hasOwnTable ) {
			this.primaryTableSource = createTableSource(
					JandexHelper.getSingleAnnotation(
							getClassInfo(),
							JPADotNames.TABLE
					)
			);
		}
		else {
			this.primaryTableSource = null;
		}

		this.secondaryTableSources = createSecondaryTableSources();
		this.customLoaderQueryName = determineCustomLoader();
		this.synchronizedTableNames = determineSynchronizedTableNames();
		this.batchSize = determineBatchSize();

		processHibernateEntitySpecificAnnotations();
		processCustomSqlAnnotations();
		processProxyGeneration();

		processDiscriminator();
	}

	public ColumnValues getDiscriminatorColumnValues() {
		return discriminatorColumnValues;
	}

    public FormulaValue getDiscriminatorFormula() {
        return discriminatorFormula;
    }

	public Class<?> getDiscriminatorType() {
		return discriminatorType;
	}

	public IdType getIdType() {
		return idType;
	}

	public boolean isExplicitPolymorphism() {
		return isExplicitPolymorphism;
	}

	public boolean isMutable() {
		return isMutable;
	}

	public OptimisticLockStyle getOptimisticLockStyle() {
		return optimisticLockStyle;
	}

	public String getWhereClause() {
		return whereClause;
	}

	public String getRowId() {
		return rowId;
	}

	public Caching getCaching() {
		return caching;
	}

	public TableSource getPrimaryTableSource() {
		if ( definesItsOwnTable() ) {
			return primaryTableSource;
		}
		else {
			return ( (EntityClass) getParent() ).getPrimaryTableSource();
		}
	}

	public Set<TableSource> getSecondaryTableSources() {
		return secondaryTableSources;
	}

	public Set<ConstraintSource> getConstraintSources() {
		return constraintSources;
	}

	public String getExplicitEntityName() {
		return explicitEntityName;
	}

	public String getEntityName() {
		return getConfiguredClass().getSimpleName();
	}

	public boolean isDynamicInsert() {
		return isDynamicInsert;
	}

	public boolean isDynamicUpdate() {
		return isDynamicUpdate;
	}

	public boolean isSelectBeforeUpdate() {
		return isSelectBeforeUpdate;
	}

	public String getCustomLoaderQueryName() {
		return customLoaderQueryName;
	}

	public CustomSQL getCustomInsert() {
		return customInsert;
	}

	public CustomSQL getCustomUpdate() {
		return customUpdate;
	}

	public CustomSQL getCustomDelete() {
		return customDelete;
	}

	public List<String> getSynchronizedTableNames() {
		return synchronizedTableNames;
	}

	public String getCustomPersister() {
		return customPersister;
	}

	public boolean isLazy() {
		return isLazy;
	}

	public String getProxy() {
		return proxy;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public boolean isEntityRoot() {
		return getParent() == null;
	}

	public boolean isDiscriminatorForced() {
		return isDiscriminatorForced;
	}

	public boolean isDiscriminatorIncludedInSql() {
		return isDiscriminatorIncludedInSql;
	}

	private String determineExplicitEntityName() {
		final AnnotationInstance jpaEntityAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), JPADotNames.ENTITY
		);
		return JandexHelper.getValue( jpaEntityAnnotation, "name", String.class );
	}


	private boolean definesItsOwnTable() {
		return !InheritanceType.SINGLE_TABLE.equals( inheritanceType ) || isEntityRoot();
	}

	private IdType determineIdType() {
		List<AnnotationInstance> idAnnotations = findIdAnnotations( JPADotNames.ID );
		List<AnnotationInstance> embeddedIdAnnotations = findIdAnnotations( JPADotNames.EMBEDDED_ID );

		if ( !idAnnotations.isEmpty() && !embeddedIdAnnotations.isEmpty() ) {
			throw new MappingException(
					"@EmbeddedId and @Id cannot be used together. Check the configuration for " + getName() + "."
			);
		}

		if ( !embeddedIdAnnotations.isEmpty() ) {
			if ( embeddedIdAnnotations.size() == 1 ) {
				return IdType.EMBEDDED;
			}
			else {
				throw new AnnotationException( "Multiple @EmbeddedId annotations are not allowed" );
			}
		}

		if ( !idAnnotations.isEmpty() ) {
            return idAnnotations.size() == 1 ? IdType.SIMPLE : IdType.COMPOSED;
		}
		return IdType.NONE;
	}

	private List<AnnotationInstance> findIdAnnotations(DotName idAnnotationType) {
		List<AnnotationInstance> idAnnotationList = new ArrayList<AnnotationInstance>();
		if ( getClassInfo().annotations().containsKey( idAnnotationType ) ) {
			idAnnotationList.addAll( getClassInfo().annotations().get( idAnnotationType ) );
		}
		ConfiguredClass parent = getParent();
		while ( parent != null ) {
			if ( parent.getClassInfo().annotations().containsKey( idAnnotationType ) ) {
				idAnnotationList.addAll( parent.getClassInfo().annotations().get( idAnnotationType ) );
			}
			parent = parent.getParent();
		}
		return idAnnotationList;
	}

	private void processDiscriminator() {
		if ( !InheritanceType.SINGLE_TABLE.equals( inheritanceType ) ) {
			return;
		}

		final AnnotationInstance discriminatorValueAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), JPADotNames.DISCRIMINATOR_VALUE
		);
		if ( discriminatorValueAnnotation != null ) {
			this.discriminatorMatchValue = discriminatorValueAnnotation.value().asString();
		}

		final AnnotationInstance discriminatorColumnAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), JPADotNames.DISCRIMINATOR_COLUMN
		);

        final AnnotationInstance discriminatorFormulaAnnotation = JandexHelper.getSingleAnnotation(
                getClassInfo(),
                HibernateDotNames.DISCRIMINATOR_FORMULA
        );


		Class<?> type = String.class; // string is the discriminator default
        if ( discriminatorFormulaAnnotation != null ) {
            String expression = JandexHelper.getValue( discriminatorFormulaAnnotation, "value", String.class );
            discriminatorFormula = new FormulaValue( getPrimaryTableSource().getExplicitTableName(), expression );
        }
         discriminatorColumnValues = new ColumnValues( null ); //(stliu) give null here, will populate values below
            discriminatorColumnValues.setNullable( false ); // discriminator column cannot be null
		if ( discriminatorColumnAnnotation != null ) {

			DiscriminatorType discriminatorType = Enum.valueOf(
					DiscriminatorType.class, discriminatorColumnAnnotation.value( "discriminatorType" ).asEnum()
			);
			switch ( discriminatorType ) {
				case STRING: {
					type = String.class;
					break;
				}
				case CHAR: {
					type = Character.class;
					break;
				}
				case INTEGER: {
					type = Integer.class;
					break;
				}
				default: {
					throw new AnnotationException( "Unsupported discriminator type: " + discriminatorType );
				}
			}

			discriminatorColumnValues.setName(
					JandexHelper.getValue(
							discriminatorColumnAnnotation,
							"name",
							String.class
					)
			);
			discriminatorColumnValues.setLength(
					JandexHelper.getValue(
							discriminatorColumnAnnotation,
							"length",
							Integer.class
					)
			);
            discriminatorColumnValues.setColumnDefinition(
						JandexHelper.getValue(
                                discriminatorColumnAnnotation,
                                "columnDefinition",
                                String.class
                        )
				);
		}
		discriminatorType = type;

		AnnotationInstance discriminatorOptionsAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.DISCRIMINATOR_OPTIONS
		);
		if ( discriminatorOptionsAnnotation != null ) {
			isDiscriminatorForced = discriminatorOptionsAnnotation.value( "force" ).asBoolean();
			isDiscriminatorIncludedInSql = discriminatorOptionsAnnotation.value( "insert" ).asBoolean();
		}
		else {
			isDiscriminatorForced = false;
			isDiscriminatorIncludedInSql = true;
		}
	}

	private void processHibernateEntitySpecificAnnotations() {
		final AnnotationInstance hibernateEntityAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.ENTITY
		);

		// see HHH-6400
		PolymorphismType polymorphism = PolymorphismType.IMPLICIT;
		if ( hibernateEntityAnnotation != null && hibernateEntityAnnotation.value( "polymorphism" ) != null ) {
			polymorphism = PolymorphismType.valueOf( hibernateEntityAnnotation.value( "polymorphism" ).asEnum() );
		}
		isExplicitPolymorphism = polymorphism == PolymorphismType.EXPLICIT;

		// see HHH-6401
		OptimisticLockType optimisticLockType = OptimisticLockType.VERSION;
		if ( hibernateEntityAnnotation != null && hibernateEntityAnnotation.value( "optimisticLock" ) != null ) {
			optimisticLockType = OptimisticLockType.valueOf(
					hibernateEntityAnnotation.value( "optimisticLock" )
							.asEnum()
			);
		}
		optimisticLockStyle = OptimisticLockStyle.valueOf( optimisticLockType.name() );

		final AnnotationInstance hibernateImmutableAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.IMMUTABLE
		);
		isMutable = hibernateImmutableAnnotation == null
				&& hibernateEntityAnnotation != null
				&& hibernateEntityAnnotation.value( "mutable" ) != null
				&& hibernateEntityAnnotation.value( "mutable" ).asBoolean();


		final AnnotationInstance whereAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.WHERE
		);
		whereClause = whereAnnotation != null && whereAnnotation.value( "clause" ) != null ?
				whereAnnotation.value( "clause" ).asString() : null;

		final AnnotationInstance rowIdAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.ROW_ID
		);
		rowId = rowIdAnnotation != null && rowIdAnnotation.value() != null
				? rowIdAnnotation.value().asString() : null;

		caching = determineCachingSettings();

		// see HHH-6397
		isDynamicInsert =
				hibernateEntityAnnotation != null
						&& hibernateEntityAnnotation.value( "dynamicInsert" ) != null
						&& hibernateEntityAnnotation.value( "dynamicInsert" ).asBoolean();

		// see HHH-6398
		isDynamicUpdate =
				hibernateEntityAnnotation != null
						&& hibernateEntityAnnotation.value( "dynamicUpdate" ) != null
						&& hibernateEntityAnnotation.value( "dynamicUpdate" ).asBoolean();


		// see HHH-6399
		isSelectBeforeUpdate =
				hibernateEntityAnnotation != null
						&& hibernateEntityAnnotation.value( "selectBeforeUpdate" ) != null
						&& hibernateEntityAnnotation.value( "selectBeforeUpdate" ).asBoolean();

		// Custom persister
		final String entityPersisterClass;
		final AnnotationInstance persisterAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.PERSISTER
		);
		if ( persisterAnnotation == null || persisterAnnotation.value( "impl" ) == null ) {
			if ( hibernateEntityAnnotation != null && hibernateEntityAnnotation.value( "persister" ) != null ) {
				entityPersisterClass = hibernateEntityAnnotation.value( "persister" ).asString();
			}
			else {
				entityPersisterClass = null;
			}
		}
		else {
			if ( hibernateEntityAnnotation != null && hibernateEntityAnnotation.value( "persister" ) != null ) {
				// todo : error?
			}
			entityPersisterClass = persisterAnnotation.value( "impl" ).asString();
		}
		this.customPersister = entityPersisterClass;
	}

	private Caching determineCachingSettings() {
		final AnnotationInstance hibernateCacheAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.CACHE
		);
		if ( hibernateCacheAnnotation != null ) {
			final org.hibernate.cache.spi.access.AccessType accessType = hibernateCacheAnnotation.value( "usage" ) == null
					? getLocalBindingContext().getMappingDefaults().getCacheAccessType()
					: CacheConcurrencyStrategy.parse( hibernateCacheAnnotation.value( "usage" ).asEnum() )
					.toAccessType();
			return new Caching(
					hibernateCacheAnnotation.value( "region" ) == null
							? getName()
							: hibernateCacheAnnotation.value( "region" ).asString(),
					accessType,
					hibernateCacheAnnotation.value( "include" ) != null
							&& "all".equals( hibernateCacheAnnotation.value( "include" ).asString() )
			);
		}

		final AnnotationInstance jpaCacheableAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), JPADotNames.CACHEABLE
		);

		boolean cacheable = true; // true is the default
		if ( jpaCacheableAnnotation != null && jpaCacheableAnnotation.value() != null ) {
			cacheable = jpaCacheableAnnotation.value().asBoolean();
		}

		final boolean doCaching;
		switch ( getLocalBindingContext().getMetadataImplementor().getOptions().getSharedCacheMode() ) {
			case ALL: {
				doCaching = true;
				break;
			}
			case ENABLE_SELECTIVE: {
				doCaching = cacheable;
				break;
			}
			case DISABLE_SELECTIVE: {
				doCaching = jpaCacheableAnnotation == null || cacheable;
				break;
			}
			default: {
				// treat both NONE and UNSPECIFIED the same
				doCaching = false;
				break;
			}
		}

		if ( !doCaching ) {
			return null;
		}

		return new Caching(
				getName(),
				getLocalBindingContext().getMappingDefaults().getCacheAccessType(),
				true
		);
	}

	/**
     * todo see {@code Binder#createTable}
	 * @param tableAnnotation a annotation instance, either {@link javax.persistence.Table} or {@link javax.persistence.SecondaryTable}
	 *
	 * @return A table source for the specified annotation instance
	 */
	private TableSource createTableSource(AnnotationInstance tableAnnotation) {
		String schema = null;
		String catalog = null;
		if ( tableAnnotation != null ) {
            schema = JandexHelper.getValue( tableAnnotation, "schema", String.class );
            catalog = JandexHelper.getValue( tableAnnotation, "catalog", String.class );
		}
		// process the table name
		String tableName = null;
		String logicalTableName = null;

		if ( tableAnnotation != null ) {
			logicalTableName = JandexHelper.getValue( tableAnnotation, "name", String.class );
			if ( StringHelper.isNotEmpty( logicalTableName ) ) {
                tableName = logicalTableName;
			}
			createUniqueConstraints( tableAnnotation, tableName );
		}

		TableSourceImpl tableSourceImpl;
		if ( tableAnnotation == null || JPADotNames.TABLE.equals( tableAnnotation.name() ) ) {
			// for the main table @Table we use 'null' as logical name
			tableSourceImpl = new TableSourceImpl( schema, catalog, tableName, null );
		}
		else {
			// for secondary tables a name must be specified which is used as logical table name
			tableSourceImpl = new TableSourceImpl( schema, catalog, tableName, logicalTableName );
		}
		return tableSourceImpl;
	}

    private Set<TableSource> createSecondaryTableSources() {
		Set<TableSource> secondaryTableSources = new HashSet<TableSource>();
        AnnotationInstance secondaryTables = JandexHelper.getSingleAnnotation(
                getClassInfo(),
                JPADotNames.SECONDARY_TABLES
        );
        AnnotationInstance secondaryTable = JandexHelper.getSingleAnnotation(
                getClassInfo(),
                JPADotNames.SECONDARY_TABLE
        );
		// collect all @secondaryTable annotations
		List<AnnotationInstance> secondaryTableAnnotations = new ArrayList<AnnotationInstance>();
        if ( secondaryTable != null ) {
            secondaryTableAnnotations.add(
                    secondaryTable
            );
        }

		if ( secondaryTables != null ) {
			secondaryTableAnnotations.addAll(
					Arrays.asList(
							JandexHelper.getValue( secondaryTables, "value", AnnotationInstance[].class )
					)
			);
		}

		// create table sources
		for ( AnnotationInstance annotationInstance : secondaryTableAnnotations ) {
			secondaryTableSources.add( createTableSource( annotationInstance ) );
		}

		return secondaryTableSources;
	}


	private void createUniqueConstraints(AnnotationInstance tableAnnotation, String tableName) {
		AnnotationValue value = tableAnnotation.value( "uniqueConstraints" );
		if ( value == null ) {
			return;
		}

		AnnotationInstance[] uniqueConstraints = value.asNestedArray();
		for ( AnnotationInstance unique : uniqueConstraints ) {
			String name = unique.value( "name" ) == null ? null : unique.value( "name" ).asString();
			String[] columnNames = unique.value( "columnNames" ).asStringArray();
			UniqueConstraintSourceImpl uniqueConstraintSource =
					new UniqueConstraintSourceImpl(
							name, tableName, Arrays.asList( columnNames )
					);
			constraintSources.add( uniqueConstraintSource );
		}
	}

	private String determineCustomLoader() {
		String customLoader = null;
		// Custom sql loader
		final AnnotationInstance sqlLoaderAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.LOADER
		);
		if ( sqlLoaderAnnotation != null ) {
			customLoader = sqlLoaderAnnotation.value( "namedQuery" ).asString();
		}
		return customLoader;
	}

	private CustomSQL createCustomSQL(AnnotationInstance customSqlAnnotation) {
		if ( customSqlAnnotation == null ) {
			return null;
		}

		final String sql = customSqlAnnotation.value( "sql" ).asString();
		final boolean isCallable = customSqlAnnotation.value( "callable" ) != null
				&& customSqlAnnotation.value( "callable" ).asBoolean();

		final ExecuteUpdateResultCheckStyle checkStyle = customSqlAnnotation.value( "check" ) == null
				? isCallable
				? ExecuteUpdateResultCheckStyle.NONE
				: ExecuteUpdateResultCheckStyle.COUNT
				: ExecuteUpdateResultCheckStyle.valueOf( customSqlAnnotation.value( "check" ).asEnum() );

		return new CustomSQL( sql, isCallable, checkStyle );
	}

	private void processCustomSqlAnnotations() {
		// Custom sql insert
		final AnnotationInstance sqlInsertAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.SQL_INSERT
		);
		customInsert = createCustomSQL( sqlInsertAnnotation );

		// Custom sql update
		final AnnotationInstance sqlUpdateAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.SQL_UPDATE
		);
		customUpdate = createCustomSQL( sqlUpdateAnnotation );

		// Custom sql delete
		final AnnotationInstance sqlDeleteAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.SQL_DELETE
		);
		customDelete = createCustomSQL( sqlDeleteAnnotation );
	}

	private List<String> determineSynchronizedTableNames() {
		final AnnotationInstance synchronizeAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.SYNCHRONIZE
		);
		if ( synchronizeAnnotation != null ) {
			final String[] tableNames = synchronizeAnnotation.value().asStringArray();
			return Arrays.asList( tableNames );
		}
		else {
			return Collections.emptyList();
		}
	}

	private void processProxyGeneration() {
		// Proxy generation
		final AnnotationInstance hibernateProxyAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.PROXY
		);
		if ( hibernateProxyAnnotation != null ) {
			isLazy = hibernateProxyAnnotation.value( "lazy" ) == null
					|| hibernateProxyAnnotation.value( "lazy" ).asBoolean();
			if ( isLazy ) {
				final AnnotationValue proxyClassValue = hibernateProxyAnnotation.value( "proxyClass" );
				if ( proxyClassValue == null ) {
					proxy = getName();
				}
				else {
					proxy = proxyClassValue.asString();
				}
			}
			else {
				proxy = null;
			}
		}
		else {
			isLazy = true;
			proxy = getName();
		}
	}

	private int determineBatchSize() {
		final AnnotationInstance batchSizeAnnotation = JandexHelper.getSingleAnnotation(
				getClassInfo(), HibernateDotNames.BATCH_SIZE
		);
		return batchSizeAnnotation == null ? -1 : batchSizeAnnotation.value( "size" ).asInt();
	}

	public String getDiscriminatorMatchValue() {
		return discriminatorMatchValue;
	}
}
