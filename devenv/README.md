## setup

#### update submodules

	$ git submodule update --init --recursive 
	
#### add required vagrant plugins

	$ vagrant plugin install vagrant-hostsmanager
	$ vagrant plugin install vagrant-omnibus
	$ vagrant plugin install vagrant-aws
	
## howto update /etc/hosts

	$ vagrant hostmanager
	
## howto instantiate VMs

note: before starting the VM make sure to build the project (RESTHeart jar is required in target directory)

on vitualbox

	$ vagrant up
	
on AWS	

	$ vagrant up --provider=aws