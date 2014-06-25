# Name of the role should match the name of the file
name "restheart-box"

default_attributes(
  "java" => {
    "install_flavor" => "oracle",
    "jdk_version" => "8",
    "oracle" => {
      "accept_oracle_download_terms" => true
    }
  }
)

# Run list function we mentioned earlier
run_list(
    "recipe[apt]",
    "recipe[java::default]",
    "recipe[git]",
    "recipe[mongodb]",
    "recipe[maven]",
    "recipe[restheart]"
)
