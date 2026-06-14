# PetAttributeValue

Union of possible pet attribute payloads stored as JSON.

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**color** | **str** |  | 
**weight_kg** | **float** |  | 
**vaccinated** | **bool** |  | 

## Example

```python
from petstore_client.models.pet_attribute_value import PetAttributeValue

# TODO update the JSON string below
json = "{}"
# create an instance of PetAttributeValue from a JSON string
pet_attribute_value_instance = PetAttributeValue.from_json(json)
# print the JSON string representation of the object
print(PetAttributeValue.to_json())

# convert the object into a dict
pet_attribute_value_dict = pet_attribute_value_instance.to_dict()
# create an instance of PetAttributeValue from a dict
pet_attribute_value_from_dict = PetAttributeValue.from_dict(pet_attribute_value_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


