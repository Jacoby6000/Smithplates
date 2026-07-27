# PetWelcome


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**message** | **str** |  | 

## Example

```python
from petstore_client.models.pet_welcome import PetWelcome

# TODO update the JSON string below
json = "{}"
# create an instance of PetWelcome from a JSON string
pet_welcome_instance = PetWelcome.from_json(json)
# print the JSON string representation of the object
print(PetWelcome.to_json())

# convert the object into a dict
pet_welcome_dict = pet_welcome_instance.to_dict()
# create an instance of PetWelcome from a dict
pet_welcome_from_dict = PetWelcome.from_dict(pet_welcome_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


