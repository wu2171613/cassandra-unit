package org.cassandraunit;

import java.util.ArrayList;
import java.util.List;

import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.beans.HSuperColumn;
import me.prettyprint.hector.api.ddl.ColumnFamilyDefinition;
import me.prettyprint.hector.api.ddl.ColumnType;
import me.prettyprint.hector.api.ddl.ComparatorType;
import me.prettyprint.hector.api.ddl.KeyspaceDefinition;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.mutation.Mutator;

import org.cassandraunit.dataset.IDataSet;
import org.cassandraunit.model.ColumnFamilyModel;
import org.cassandraunit.model.ColumnModel;
import org.cassandraunit.model.KeyspaceModel;
import org.cassandraunit.model.RowModel;
import org.cassandraunit.model.SuperColumnModel;
import org.cassandraunit.serializer.GenericTypeSerializer;
import org.cassandraunit.type.GenericType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLoader {
	Cluster cluster = null;

	private Logger log = LoggerFactory.getLogger(DataLoader.class);

	public DataLoader(String clusterName, String host) {
		super();
		cluster = HFactory.getOrCreateCluster(clusterName, host);
	}

	protected Cluster getCluster() {
		return cluster;
	}

	public void load(IDataSet dataSet) {
		KeyspaceModel dataSetKeyspace = dataSet.getKeyspace();

		dropKeyspaceIfExist(dataSetKeyspace.getName());

		List<ColumnFamilyDefinition> columnFamilyDefinitions = createColumnFamilyDefinitions(dataSet, dataSetKeyspace);

		KeyspaceDefinition keyspaceDefinition = HFactory.createKeyspaceDefinition(dataSetKeyspace.getName(),
				dataSetKeyspace.getStategy(), dataSetKeyspace.getReplicationFactor(), columnFamilyDefinitions);

		cluster.addKeyspace(keyspaceDefinition);

		log.info("creating keyspace : {}", keyspaceDefinition.getName());
		Keyspace keyspace = HFactory.createKeyspace(dataSet.getKeyspace().getName(), cluster);
		log.info("loading data into keyspace : {}", keyspaceDefinition.getName());
		loadData(dataSet, keyspace);
	}

	private void dropKeyspaceIfExist(String keyspaceName) {
		KeyspaceDefinition existedKeyspace = cluster.describeKeyspace(keyspaceName);
		if (existedKeyspace != null) {
			log.info("dropping existing keyspace : {}", existedKeyspace.getName());
			cluster.dropKeyspace(keyspaceName);
		}
	}

	private void loadData(IDataSet dataSet, Keyspace keyspace) {
		for (ColumnFamilyModel columnFamily : dataSet.getColumnFamilies()) {
			loadColumnFamilyData(columnFamily, keyspace);
		}

	}

	private void loadColumnFamilyData(ColumnFamilyModel columnFamily, Keyspace keyspace) {
		Mutator<GenericType> mutator = HFactory.createMutator(keyspace, GenericTypeSerializer.get());
		for (RowModel row : columnFamily.getRows()) {
			switch (columnFamily.getType()) {
			case STANDARD:
				for (HColumn<GenericType, GenericType> hColumn : createHColumnList(row.getColumns())) {
					mutator.addInsertion(row.getKey(), columnFamily.getName(), hColumn);
				}

				break;
			case SUPER:
				for (SuperColumnModel superColumnModel : row.getSuperColumns()) {
					HSuperColumn<GenericType, GenericType, GenericType> column = HFactory.createSuperColumn(
							superColumnModel.getName(), createHColumnList(superColumnModel.getColumns()),
							GenericTypeSerializer.get(), GenericTypeSerializer.get(), GenericTypeSerializer.get());
					mutator.addInsertion(row.getKey(), columnFamily.getName(), column);
				}
				break;
			default:
				break;
			}

		}
		mutator.execute();

	}

	private List<HColumn<GenericType, GenericType>> createHColumnList(List<ColumnModel> columnsModel) {
		List<HColumn<GenericType, GenericType>> hColumns = new ArrayList<HColumn<GenericType, GenericType>>();
		for (ColumnModel columnModel : columnsModel) {
			HColumn<GenericType, GenericType> column = HFactory.createColumn(columnModel.getName(),
					columnModel.getValue(), GenericTypeSerializer.get(), GenericTypeSerializer.get());
			hColumns.add(column);
		}
		return hColumns;
	}

	private List<ColumnFamilyDefinition> createColumnFamilyDefinitions(IDataSet dataSet, KeyspaceModel dataSetKeyspace) {
		List<ColumnFamilyDefinition> columnFamilyDefinitions = new ArrayList<ColumnFamilyDefinition>();
		for (ColumnFamilyModel columnFamily : dataSet.getColumnFamilies()) {
			ColumnFamilyDefinition cfDef = HFactory.createColumnFamilyDefinition(dataSetKeyspace.getName(),
					columnFamily.getName());
			cfDef.setColumnType(columnFamily.getType());

			cfDef.setKeyValidationClass(columnFamily.getKeyType().getClassName());
			cfDef.setComparatorType(ComparatorType.getByClassName(columnFamily.getComparatorType().getClassName()));
			if (columnFamily.getType().equals(ColumnType.SUPER) && columnFamily.getSubComparatorType() != null) {
				cfDef.setSubComparatorType(columnFamily.getSubComparatorType());
			}

			columnFamilyDefinitions.add(cfDef);
		}
		return columnFamilyDefinitions;
	}
}