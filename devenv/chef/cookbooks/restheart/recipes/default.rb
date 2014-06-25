execute "build" do
	command "cd /restart; mvn package"
	only_if { ::File.exists?("/restart/.git") }
end

cookbook_file "restart.yml" do
	path "/home/vagrant/restart-chef.yml" 
	mode 0644
	owner "root"
end

execute "run" do
	command "killall java; nohup java -server -jar /restart/target/RESTart-1.0-SNAPSHOT-jar-with-dependencies.jar /home/vagrant/restart-chef.yml &"
	only_if { ::File.exists?("/restart/target/RESTart-1.0-SNAPSHOT-jar-with-dependencies.jar") }
end