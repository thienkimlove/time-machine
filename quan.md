### Setup for running

1. Install docker mysql master and slave replication for testing
```bash
#install docker ubuntu https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-16-04
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
sudo apt-get update
sudo apt-get install -y docker-ce



# https://www.percona.com/blog/2016/03/30/docker-mysql-replication-101/
# Create local data directories
mkdir -p /opt/Docker/masterdb/data /opt/Docker/slavedb/data
# Create local my.cnf directories
mkdir -p /opt/Docker/masterdb/cnf /opt/Docker/slavedb/cnf
### Create configuration files for master and slave
vi /opt/Docker/masterdb/cnf/config-file.cnf
# Config Settings:
#[mysqld]
#server-id=1
#binlog_format=ROW
#log-bin
vi /opt/Docker/slavedb/cnf/config-file.cnf
## Config Settings:
#[mysqld]
#server-id=2
#binlog_format=ROW
#log-bin

# Launch master instance
docker run --name masterdb -v /opt/Docker/masterdb/cnf:/etc/mysql/conf.d -v /opt/Docker/masterdb/data:/var/lib/mysql -p 33060:3306 -e MYSQL_ROOT_PASSWORD=tieungao -d percona:5.7

# Create replication user
docker exec -ti masterdb 'mysql' -uroot -ptieungao -vvv -e "GRANT REPLICATION SLAVE ON *.* TO repl@'%' IDENTIFIED BY 'tieungao';"

### Get master status
docker exec -ti masterdb 'mysql' -uroot -ptieungao -e"SHOW MASTER STATUS"

#mysql: [Warning] Using a password on the command line interface can be insecure.
#*************************** 1. row ***************************
#             File: mysqld-bin.000004
#         Position: 310
#

docker run --name slavedb -d -v /opt/Docker/slavedb/cnf:/etc/mysql/conf.d -v /opt/Docker/slavedb/data:/var/lib/mysql -p 33061:3306 --link masterdb:mysql -e MYSQL_ROOT_PASSWORD=tieungao -d percona:5.7

#using file_name and log_pos from master show status.

docker exec -ti slavedb 'mysql' -uroot -ptieungao -e'change master to master_host="mysql",master_user="repl",master_password="tieungao",master_log_file="f49a116df8d7-bin.000003",master_log_pos=437;' -vvv


# Start replication
docker exec -ti slavedb 'mysql' -uroot -ptieungao -e"START SLAVE;" -vvv

# Verify replication is running OK
docker exec -ti slavedb 'mysql' -uroot -ptieungao -e"SHOW SLAVE STATUS" -vvv

#Create test table

***This have no Primary ID will make error Index : 0, Size : 0***

# CREATE TABLE pet (name VARCHAR(20), owner VARCHAR(20), species VARCHAR(20), sex CHAR(1), birth DATE, death DATE);
# INSERT INTO test.pet (name, owner, species, sex, birth, death) VALUE('test', 'quan', '10', 'f', '2017-10-10', '2018-10-10')

```

We must using 

```text

CREATE TABLE `products`.`users` (
  `remember_token` text,
  `name` text,
  `created_at` text,
  `updated_at` text,
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `password` text,
  `email` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=latin1;


INSERT INTO `products`.`users`
(`remember_token`,
`name`,
`created_at`,
`updated_at`,
`password`,
`email`)
VALUES
('test',
'tieungao',
NOW(),
NOW(),
'abadfasdfasdfasdfasdfasdf',
'whenoff@yahoo.com');

```


testing database information
```
Master DB
Host : 103.7.41.141
Port : 33060
User : root
Pass : tieungao

Slave DB
Host : 103.7.41.141
Port : 33061
User : root
Pass : tieungao
```
2. Start Hadoop, Hbase on 42.112.31.173
```bash
su - hadoop
/usr/local/hadoop/sbin/start-dfs.sh
/usr/local/hadoop/sbin/start-yarn.sh
/usr/local/hbase/bin/start-hbase.sh
```
3. About Hbase
Start HBase Shell : `/usr/local/hbase/bin/hbase shell`

Notice that we run HBase in `Pseudo-distributed mode` and using HDFS to store HBase data in `/hbase` as describer below
```
Pseudo-distributed mode means that HBase still runs completely on a single host, but each HBase daemon (HMaster, HRegionServer, and ZooKeeper) runs as a separate process: in standalone mode all daemons ran in one jvm process/instance. By default, unless you configure the hbase.rootdir property as described in quickstart, your data is still stored in /tmp/. In this walk-through, we store your data in HDFS instead, assuming you have HDFS available
```
Show the HBase directory on HDFS `hadoop fs -ls /hbase`

HMaster server
```
The HMaster server controls the HBase cluster. You can start up to 9 backup HMaster servers, which makes 10 total HMasters, counting the primary. To start a backup HMaster, use the local-master-backup.sh
```
RegionServers
```
The HRegionServer manages the data in its StoreFiles as directed by the HMaster. Generally, one HRegionServer runs per node in the cluster
```
About ZooKeeper Default Configuration : `http://hbase.apache.org/book.html#hbase_default_configurations`

We using default configuration for Zookeeper because only need to change in advanced-mode.
```
hbase.zookeeper.quorum
    Description
    Comma separated list of servers in the ZooKeeper ensemble (This config. should have been named hbase.zookeeper.ensemble). For example, "host1.mydomain.com,host2.mydomain.com,host3.mydomain.com". By default this is set to localhost for local and pseudo-distributed modes of operation. For a fully-distributed setup, this should be set to a full list of ZooKeeper ensemble servers. If HBASE_MANAGES_ZK is set in hbase-env.sh this is the list of servers which hbase will start/stop ZooKeeper on as part of cluster start/stop. Client-side, we will take this list of ensemble members and put it together with the hbase.zookeeper.property.clientPort config. and pass it into zookeeper constructor as the connectString parameter.
    Default
    localhost
```
4. Start with Binlog Flusher (`https://github.com/mysql-time-machine/replicator/tree/master/binlog-flusher`)

Flushes MySQL database tables to the binlog in order to have the initial snapshot of the database in the binlog.

Run command `python data-flusher.py --host=103.7.41.141 --user=root --passwd=tieungao --port=33061` to flush bin log for this slave server.

5. MySQL Replicator

Replicates data changes from MySQL binlog to HBase or Kafka. In case of HBase, preserves the previous data versions. HBase storage is intended for auditing purposes of historical data. In addition, special daily-changes tables can be maintained in HBase, which are convenient for fast and cheap imports from HBase to Hive. Replication to Kafka is intended for easy real-time access to a stream of data changes.

Build `mvn package`

```bash
[INFO] Replacing original artifact with shaded artifact.
[INFO] Replacing /opt/Docker/applierdb/time-machine/target/mysql-replicator-0.14.2.jar with /opt/Docker/applierdb/time-machine/target/mysql-replicator-0.14.2-shaded.jar
[INFO] Dependency-reduced POM written at: /opt/Docker/applierdb/time-machine/dependency-reduced-pom.xml
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 13:20 min
[INFO] Finished at: 2017-11-03T13:43:32+07:00
[INFO] Final Memory: 70M/650M
[INFO] ------------------------------------------------------------------------
```
We make change abit in Java Source and Test to allow mysql from other port than 3306.

**Enable HBase Snapshot**
```
<property>
    <name>hbase.snapshot.enabled</name>
    <value>true</value>
  </property>
```

Check mysql bin log to `log_format=ROW` and we test with

```
java -jar ../../target/mysql-replicator-0.14.2.jar --applier STDOUT --schema test --binlog-filename mysql-bin.000001 --config-path ./simple_stdout.yml
```

```
java -jar ../../target/mysql-replicator-0.14.2.jar --hbase-namespace today --applier hbase --config-path ./hbase_dryrun.yml --delta
```



Currently we have problem with write to HBase table.

#### Final

1. we build server `git fetch && git pull && mvn package`

2. Create database for `simple test` and `init run first time`

3. Run `python data-flusher.py --host=103.7.41.141 --user=root --passwd=tieungao --port=33061` 

4. Run `java -jar ../../target/mysql-replicator-0.14.2.jar --applier STDOUT --schema test --binlog-filename mysql-bin.000001 --config-path ./simple_stdout.yml` for testing.

5. Run at first time

```bash
java -jar ../../target/mysql-replicator-0.14.2.jar \
    --schema test \
    --applier hbase \
    --binlog-filename mysql-bin.000001 \
    --config-path  ./hbase_dryrun.yml \
    --initial-snapshot
```
6. Wait til it completed.

7. Final

-  We connected direct to master

-  Mysql Configuration as below :

```text
[mysqld]
server_id = 99999
# Binlog configuration
log-bin          = mysqlbin
binlog_format    = ROW
binlog_row_image = full
log_slave_updates
max_binlog_size  = 100M

```

- Create heatbeat database on meta-store-database

```text
CREATE TABLE IF NOT EXISTS db_heartbeat (
    server_id int unsigned NOT NULL,
    csec bigint unsigned DEFAULT NULL,
    PRIMARY KEY (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Insert the initial row
INSERT INTO db_heartbeat VALUES ( @@global.server_id, 100 * UNIX_TIMESTAMP(NOW(2)) );

-- Create the event to update this row every 1s
DELIMITER $$

CREATE
  DEFINER=`root`@`localhost`
  EVENT `db_heartbeat`
  ON SCHEDULE EVERY 1 SECOND
  ON COMPLETION PRESERVE ENABLE
DO
BEGIN
  DECLARE result INT;
  DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
  SET innodb_lock_wait_timeout = 1;
  SET result = GET_LOCK( 'db_heartbeat', 0 );
  IF result = 1 THEN
    UPDATE db_heartbeat SET csec=100*UNIX_TIMESTAMP(NOW(2)) WHERE server_id = @@global.server_id;
    SET result = RELEASE_LOCK( 'db_heartbeat' );
  END IF;
END$$db_heartbeat
DELIMITER ;

```

- First we run with simple STDOUT to see if it can read from bin log or not.

```bash
java -jar ../../target/mysql-replicator-0.14.2.jar \
    --applier STDOUT \
    --schema test \
    --binlog-filename mysql-bin.000001 \
    --config-path  ./simple_stdout.yml

```
- If can not read then we must run bin log flusher to 
create bin log from beginning so we have `CREATE table`.

```bash
python data-flusher.py --host=103.7.41.141 --user=root --passwd=tieungao --port=33060
```

Because it run on master so maybe we need to config more.

Or setup to run on slave.

- After that run the snapshot init

```bash
java -jar ../../target/mysql-replicator-0.14.2.jar \
    --schema products \
    --applier hbase \
    --binlog-filename mysqlbin.000001 \
    --config-path  ./hbase_dryrun.yml \
    --initial-snapshot

```

When it seems finish to create HBase Tables. We stop this.

- Run the replication

```bash
java -jar ../../target/mysql-replicator-0.14.2.jar \
    --schema products \
    --applier hbase \
    --config-path  ./hbase_dryrun.yml 
```
- SQL for Create tables and insert row

```text
CREATE TABLE `products`.`users` (
  `remember_token` text,
  `name` text,
  `created_at` text,
  `updated_at` text,
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `password` text,
  `email` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=latin1;

INSERT INTO `products`.`users`
(`remember_token`,
`name`,
`created_at`,
`updated_at`,
`password`,
`email`)
VALUES
('test',
'tieungao',
NOW(),
NOW(),
'abadfasdfasdfasdfasdfasdf',
'whenoff@yahoo.com');


```

We can running with nohup

```bash
nohup java -jar ../../target/mysql-replicator-0.14.2.jar     --schema products     --applier hbase     --config-path  ./hbase_dryrun.yml &
```

Read `cat nohup.out` for details.

When create new tables, we must using `CREATE Database.TableName` without ```. Please check 

```text
LOGGER.warn("No Db name in Query Event. Extracted SQL: " + ((QueryEvent) event).getSql().toString());
```

on `PipelineOrchestrator.java`

#### Install Hue for Easy manager Hbase Database.

We go to `http://gethue.com/hue-4-1-is-out/`

```xml
<configuration>
<property>
   <name>hbase.rootdir</name>
   <value>hdfs://localhost:54310/hbase</value>
</property>

<property>
   <name>hbase.zookeeper.property.dataDir</name>
   <value>/home/hadoop/zookeeper2</value>
</property>
<property>
    <name>hbase.cluster.distributed</name>
    <value>true</value>
</property>
<!-- Thrift Server 1 Configuration so Hue can access -->

<property>
 <name>hbase.regionserver.thrift.http</name>
  <value>true</value>
</property>
<property>
  <name>hbase.thrift.support.proxyuser</name>
  <value>true</value>
</property>
<property>
  <name>hadoop.security.authorization</name>
  <value>true</value>
</property>
<property>
  <name>hadoop.proxyuser.hadoop.groups</name>
  <value>*</value>
</property>
<property>
  <name>hadoop.proxyuser.hadoop.hosts</name>
  <value>*</value>
</property>

</configuration>
```


```bash

#start Hbase thirft server
hbase-daemon.sh start thrift
# start ozzie server
oozied.sh start
# start hiveserver2
hive --service hiveserver2
# start Hue
cd /tmp/hue

sudo ufw allow 8888/tcp
# edit and change port to 8888
build/env/bin/supervisor

```

#### Deep on Replication code

```text
// COMMIT does not always contain database name so we get it
// from current transaction metadata.
// There is an assumption that all tables in the transaction
// are from the same database. Cross database transactions
// are not supported.
```

1. Start setup on MasterDB

```text
server-id		= 1
# in /var/lib/mysql/mysql-bin.xxx
log_bin			= mysql-bin
# For BinLogParse can reading.

binlog_format           = ROW
# If SlaveDB stop for 10 days more, we must setup replication again.
expire_logs_days	= 10
max_binlog_size   = 100M
# We only want one database write to binlog
binlog_do_db		= live_warehouse_v2
# Need setup
innodb_flush_log_at_trx_commit = 1
sync_binlog             = 1

```

2. Setup replication

```bash
CREATE USER replicant@103.21.150.80;
GRANT REPLICATION SLAVE ON *.* TO replicant@103.21.150.80 IDENTIFIED BY 'pw_replicant_slave'
```
3. Install fresh MYSQL server on Slave

```bash
sudo apt remove --purge mysql-server mysql-client mysql-common
sudo rm -rf /var/lib/mysql*
sudo rm -rf /etc/mysql
sudo rm -rf /var/log/mysql
sudo apt-get autoremove
sudo apt-get autoclean

#Then reinstall:

sudo apt-get update
sudo apt-get install mysql-server

service mysql stop

```

Edit Mysql Configuration 

```text

server-id               = 101
binlog-format           = ROW
log_bin                 = mysql-bin
relay-log               = mysql-relay-bin
log-slave-updates = 1
read-only               = 1
expire_logs_days        = 10
max_binlog_size   = 100M
binlog_do_db            = live_warehouse_v2

```

Dum Mysql from master

```bash
mysqldump --skip-lock-tables -uroot -ptieungao --single-transaction --flush-logs --hex-blob --master-data=2 live_warehouse_v2 > /tmp/slave.sql
head slave.sql -n80 | grep "MASTER_LOG_POS"
#remember this information
#CHANGE MASTER TO MASTER_LOG_FILE='mysql-bin.000002', MASTER_LOG_POS=154;
scp /tmp/slave.sql quan-dev@103.21.150.80:/tmp

CHANGE MASTER TO MASTER_HOST='103.21.150.81',MASTER_USER='replicant',MASTER_PASSWORD='pw_replicant_slave', MASTER_LOG_FILE='mysql-bin.000002', MASTER_LOG_POS=154;
START SLAVE;
#https://plusbryan.com/mysql-replication-without-downtime
SHOW SLAVE STATUS;
mysql> GRANT ALL PRIVILEGES ON live_warehouse_v2.* TO 'slave'@'%' IDENTIFIED BY 'slave123';
Query OK, 0 rows affected, 1 warning (0.00 sec)

mysql> FLUSH PRIVILEGES;
Query OK, 0 rows affected (0.00 sec)
```

#### Re-install all Hadoop Software by login to hadoop user.


1. Hadoop (`http://www.bogotobogo.com/Hadoop/BigData_hadoop_Install_on_ubuntu_16_04_single_node_cluster.php`)

```text
wget http://mirror.downloadvn.com/apache/hadoop/common/hadoop-2.8.1/hadoop-2.8.1.tar.gz
sudo ln -s /opt/hadoop-2.8.1 /usr/local/hadoop
vim /usr/local/hadoop/etc/hadoop/hadoop-env.sh
export JAVA_HOME=$(readlink -f /usr/bin/java | sed "s:bin/java::")


Put in /usr/local/hadoop/etc/hadoop/hdfs-site.xml

<configuration>
 <property>
  <name>dfs.replication</name>
  <value>1</value>
  <description>Default block replication.
  The actual number of replications can be specified when the file is created.
  The default is used if replication is not specified in create time.
  </description>
 </property>
 <property>
   <name>dfs.namenode.name.dir</name>
   <value>file:/opt/hadoop_store/hdfs/namenode</value>
 </property>
 <property>
   <name>dfs.datanode.data.dir</name>
   <value>file:/opt/hadoop_store/hdfs/datanode</value>
 </property>
 <property>
  <name>dfs.webhdfs.enabled</name>
  <value>true</value>
</property> 
</configuration>

mkdir -p /opt/hadoop_store/hadoop/tmp
mkdir -p /opt/hadoop_store/hdfs/namenode
mkdir -p /opt/hadoop_store/hdfs/datanode

Put in core-site.xml

<configuration>
     <property>
      <name>hadoop.tmp.dir</name>
      <value>/opt/hadoop_store/hadoop/tmp</value>
      <description>A base for other temporary directories.</description>
     </property>

     <property>
      <name>fs.default.name</name>
      <value>hdfs://localhost:9000</value>
      <description>The name of the default file system.  A URI whose
      scheme and authority determine the FileSystem implementation.  The
      uri's scheme determines the config property (fs.SCHEME.impl) naming
      the FileSystem implementation class.  The uri's authority is used to
      determine the host, port, etc. for a filesystem.</description>
     </property>
     <property>
      <name>hadoop.proxyuser.hadoop.hosts</name>
      <value>*</value>
    </property>
    <property>
      <name>hadoop.proxyuser.hadoop.groups</name>
      <value>*</value>
    </property>
</configuration>

Edit mapped-site.xml

<configuration>
 <property>
  <name>mapred.job.tracker</name>
  <value>localhost:9001</value>
  <description>The host and port that the MapReduce job tracker runs
  at.  If "local", then jobs are run in-process as a single map
  and reduce task.
  </description>
 </property>
</configuration>

Run only one time.

hadoop namenode -format

Visit 
http://103.21.150.80:50070

```

2. Install Hive (`https://hadoop7.wordpress.com/2017/01/27/installing-hive-on-ubuntu-16-04/`)

```text
wget http://mirror.downloadvn.com/apache/hive/stable-2/apache-hive-2.1.1-bin.tar.gz
sudo ln -s /opt/apache-hive-2.1.1-bin /usr/local/hive
hdfs dfs -mkdir -p /user/hive/warehouse
hdfs dfs -mkdir /tmp
hdfs dfs -chmod g+w /tmp
hdfs dfs -chmod g+w /user/hive/warehouse


export HIVE_HOME=/usr/local/hive
export HIVE_CONF_DIR=/usr/local/hive/conf
export PATH=$HIVE_HOME/bin:$PATH
export CLASSPATH=$CLASSPATH:/usr/local/hadoop/lib/*:.
export CLASSPATH=$CLASSPATH:/usr/local/hive/lib/*:.

rm lib/log4j-slf4j-impl-2.4.1.jar

mkdir -p /opt/hadoop_store/hive
schematool -dbType derby -initSchema
hive -e "show databases;"
hiveserver2
http://103.21.150.80:10002/hiveserver2.jsp
```


3. Oozie 4.3.0 (`https://big-data-sciences.blogspot.com/2016/12/install-apache-oozie-430-in-hadoop-273.html`)

```bash
wget http://mirror.downloadvn.com/apache/oozie/4.3.0/oozie-4.3.0.tar.gz
sudo mv oozie-4.3.0 /opt/
cd /opt/oozie-4.3.0
bin/mkdistro.sh -DskipTests -Dhadoopversion=2.8.1
sudo ln -s /opt/oozie-4.3.0/distro/target/oozie-4.3.0-distro/oozie-4.3.0 /usr/local/oozie
```
Put in `~/.bashrc` :

```text
export OOZIE_VERSION=4.3.0
export OOZIE_HOME=/usr/local/oozie
export PATH=$PATH:$OOZIE_HOME/bin


cd $OOZIE_HOME
mkdir libext

cd /usr/local/hadoop
cp share/hadoop/common/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/common/lib/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/hdfs/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/hdfs/lib/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/mapreduce/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/mapreduce/lib/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/yarn/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/yarn/lib/*.jar /usr/local/sqoop/server/lib/
 

/usr/local/hadoop$ cp etc/hadoop/core-site.xml /usr/local/oozie/conf/hadoop-conf
/usr/local/hadoop$ cp etc/hadoop/yarn-site.xml /usr/local/oozie/conf/hadoop-conf
/usr/local/hadoop$ cp etc/hadoop/mapred-site.xml /usr/local/oozie/conf/hadoop-conf

```

Install Sqoop2 :

```text
Install sqoop 2 tutorial 
http://cleverowl.uk/2015/08/07/importing-data-from-oracle-rdbms-into-hadoop-using-apache-sqoop-2/

http://gethue.com/move-data-in-out-your-hadoop-cluster-with-the-sqoop/

debug in sqoop2-shell set option --name verbose --value true

when create link for hdfs, we leave uri blank

All about document for sqoop 2 : https://sqoop.apache.org/docs/1.99.6/

wget https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-5.1.44.tar.gz

export SQOOP_HOME=/usr/local/sqoop
export PATH=$PATH:$SQOOP_HOME/bin
export SQOOP_CONF_DIR=$SQOOP_HOME/conf
export SQOOP_CLASS_PATH=$SQOOP_CONF_DIR

cd /usr/local/hadoop

cp share/hadoop/common/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/common/lib/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/hdfs/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/hdfs/lib/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/mapreduce/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/mapreduce/lib/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/yarn/*.jar /usr/local/sqoop/server/lib/ && cp share/hadoop/yarn/lib/*.jar /usr/local/sqoop/server/lib/

```

Running in Server

- **Hadoop** : run `start-dfs.sh` and `start-yarn.sh`.

- **Hue** Go to `/opt/hue` and run `nohup build/env/bin/supervisor &`.

- **HiveServer2** : go to /opt/tmp and run `nohup hiveserver2 &` . Shell `hive` or run command in shell with `hive -e 'command'`

- **HBase** : run `start-hbase.sh` for HBase and `hbase-daemon.sh start thrift` for Thrift 1 Server needed for Hue. Shell `hbase shell`

- **Oozie** : run `oozied.sh start`

- **Sqoop2** : run `sqoop.sh server start` and shell `sqoop2-shell`


### when work with database and using binlog flusher

```bash
 python data-flusher.py --host=localhost --user=root --passwd=tieungao --port=3306 --db=live_warehouse_v2

python db-recovery.py --mycnf /etc/mysql/my.cnf  --db live_warehouse_v2  --host localhost --hashfile /opt/time-machine/binlog-flusher/tmp/flusherHash-2017-11-05-23-19.txt

```

Mysql Drop Contraint 

```sql
ALTER TABLE conversation_tags
DROP FOREIGN KEY 'conversation_tags_ibfk_1';
```

So we have 3 problems when import database to HBase

1. error on contrain key name  = key name in mysql table (Must remove it)

2. 2 table `checksums` and `task_result` have problem with 

```text
jdbc4.MySQL Data Exception: '4.294967295E9'  is outside valid range for the datatype INTEGER
https://issues.apache.org/jira/browse/SQOOP-341
```

Currently i just drop those tables.

3. Problem with no PK column (Seems that we have many data in bin log, which about some table already deleted)

current if not have hbaseRowId 

```text
 Long columnTimestampforRowKey = row.getEventV4Header().getTimestamp();
        // RowID
        String hbaseRowID = getHBaseRowKey(row);

        if (hbaseRowID == null) {
            hbaseRowID = "undefined" + columnTimestampforRowKey;
        }

```


Next we must continue with `git@github.com:mysql-time-machine/hbase-snapshotter.git`