apt_repository 'ubuntu-upstart' do
  uri        'http://downloads-distro.mongodb.org/repo/ubuntu-upstart'
  components ['dist', '10gen']
  keyserver  'hkp://keyserver.ubuntu.com:80'
  key        '7F0CEB10'
end

apt_package "mongodb-org" do
 	action :install
end

execute "create_mongo_admin" do
	command "touch /root/mongo-initialized && mongo admin --eval \"db.dropUser(\\\"admin\\\"); db.createUser( { user: \\\"admin\\\", pwd: \\\"adminadmin\\\",  roles: [ \\\"root\\\" ] } ) \""
	not_if { ::File.exists?("/root/mongo-initialized") }
end

cookbook_file "mongodb.conf" do
	path "/etc/mongodb.conf" 
	mode 0644
	owner "root"
end

service "mongod" do
	action :restart
end

execute "delay_for_mongo_restart" do
	command "sleep 3"
end

execute "delete_test_data" do
	command "mongo testdb --authenticationDatabase admin -u admin -p \"adminadmin\" --eval \"db.testcoll.drop();\""
end

execute "create_test_data" do
	command "mongo testdb --authenticationDatabase admin -u admin -p \"adminadmin\" --eval \"db.testcoll.insert( { name: \\\"Andrea\\\", surname: \\\"Di Cesare\\\", phone: { type: \\\"mobile\\\", no: \\\"329.7376417\\\"} });\""
end
