## setup

### update submodules

	$ git submodule update --init --recursive 
	
### add required vagrant plugins

	$ vagrant plugin install vagrant-hostsmanager
	$ vagrant plugin install vagrant-omnibus
	$ vagrant plugin install vagrant-aws
	
### setup AWS

only needed if you want to provision the VMs on AWS

#### aws security group

you need to edit the default security group adding the following rules:
- TCP port 4443 incoming
- TCP port 27017 incoming

#### aws private properties

in order to provision the VMs on AWS, you need to set the following properties on vagrant-conf.yml file (you need to create it in the same dir than Vagrantfile):
	
	---
	aws_access_key: <your aws accecss key>
	aws_secret_access_key: <your aws secret>
	ssh_private_key_path: <path to your aws ssh key>

#### AWS region and AMI

the VM will be provisioned in the eu-west-1. if you change the region you'll need to update the ami also (make sure to match the OS)
	
## howto update /etc/hosts

	$ vagrant hostmanager
	
## howto privision the VMs

before provisioning the VM make sure to build the project (RESTHeart jar is required in target directory)

#### on vitualbox

	$ vagrant up
	
#### on AWS


	$ vagrant up --provider=aws
    $ vagrant hostmanager --provider=aws