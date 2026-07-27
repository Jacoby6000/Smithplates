# PetPing


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**nonce** | **str** |  | 

## Example

```python
from petstore_client.models.pet_ping import PetPing

# TODO update the JSON string below
json = "{}"
# create an instance of PetPing from a JSON string
pet_ping_instance = PetPing.from_json(json)
# print the JSON string representation of the object
print(PetPing.to_json())

# convert the object into a dict
pet_ping_dict = pet_ping_instance.to_dict()
# create an instance of PetPing from a dict
pet_ping_from_dict = PetPing.from_dict(pet_ping_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


