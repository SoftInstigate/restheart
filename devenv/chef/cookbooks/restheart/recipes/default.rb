#execute "build" do
#	command "cd /restheart; mvn package"
#	only_if { ::File.exists?("/restheart/.git") }
#end

cookbook_file "restheart.yml" do
	path "/etc/restheart.yml" 
	mode 0644
	owner "root"
end

cookbook_file "restheartd.conf" do
	path "/etc/init/restheartd.conf" 
	mode 0644
	owner "root"
end

service "restheartd" do
	provider Chef::Provider::Service::Upstart
	action :restart
end