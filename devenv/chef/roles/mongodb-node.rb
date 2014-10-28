# Name of the role should match the name of the file
name "mongodb-node"

# Run list function we mentioned earlier
run_list(
    "recipe[apt]",
    "recipe[mongodb]",
)
