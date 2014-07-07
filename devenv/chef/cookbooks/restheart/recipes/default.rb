execute "build" do
	command "cd /restheart; mvn package"
	only_if { ::File.exists?("/restheart/.git") }
end

cookbook_file "restheart.yml" do
	path "/home/vagrant/restheart-chef.yml" 
	mode 0644
	owner "root"
end

execute "run" do
	command "killall java; nohup java -server -jar /restheart/target/restheart-1.0-SNAPSHOT-jar-with-dependencies.jar /home/vagrant/restheart-chef.yml &"
	only_if { ::File.exists?("/restheart/target/restheart-1.0-SNAPSHOT-jar-with-dependencies.jar") }
end