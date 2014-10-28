apt_repository 'ubuntu-upstart' do
  uri        'http://downloads-distro.mongodb.org/repo/ubuntu-upstart'
  components ['dist', '10gen']
  keyserver  'hkp://keyserver.ubuntu.com:80'
  key        '7F0CEB10'
end

apt_package "mongodb-org" do
 	action :install
end

cookbook_file "mongod.conf" do
	path "/etc/mongod.conf" 
	mode 0644
	owner "root"
end

service "mongod" do
	action [ :enable, :start ]
end