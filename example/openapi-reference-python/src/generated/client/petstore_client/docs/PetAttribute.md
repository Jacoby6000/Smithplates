# PetAttribute


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **str** |  | 
**value** | [**PetAttributeValue**](PetAttributeValue.md) |  | 

## Example

```python
from petstore_client.models.pet_attribute import PetAttribute

# TODO update the JSON string below
json = "{}"
# create an instance of PetAttribute from a JSON string
pet_attribute_instance = PetAttribute.from_json(json)
# print the JSON string representation of the object
print(PetAttribute.to_json())

# convert the object into a dict
pet_attribute_dict = pet_attribute_instance.to_dict()
# create an instance of PetAttribute from a dict
pet_attribute_from_dict = PetAttribute.from_dict(pet_attribute_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


