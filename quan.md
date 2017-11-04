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

```sql


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

```sql
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

```sql
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

```sql
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

Next we must continue with `git@github.com:mysql-time-machine/hbase-snapshotter.git`