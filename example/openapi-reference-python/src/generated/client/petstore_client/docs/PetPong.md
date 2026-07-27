# PetPong


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**nonce** | **str** |  | 

## Example

```python
from petstore_client.models.pet_pong import PetPong

# TODO update the JSON string below
json = "{}"
# create an instance of PetPong from a JSON string
pet_pong_instance = PetPong.from_json(json)
# print the JSON string representation of the object
print(PetPong.to_json())

# convert the object into a dict
pet_pong_dict = pet_pong_instance.to_dict()
# create an instance of PetPong from a dict
pet_pong_from_dict = PetPong.from_dict(pet_pong_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


