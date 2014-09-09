

##setu


####update submodules

	$ git submodule update

####add required vagrant plugins

	$ vagrant plugin install vagrant-hostsmanager
	$ vagrant plugin install vagrant-omnibus
	$ vagrant plugin install vagrant-aws
	
## howto update /etc/hosts

	$ vagrant hostmanager
	
## howto instantiate VMs

on vitualbox

	$ vagrant up
	
on AWS	

	$ vagrant up --provider=aws

