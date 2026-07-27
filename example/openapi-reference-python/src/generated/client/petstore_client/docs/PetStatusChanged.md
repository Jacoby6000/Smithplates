# PetStatusChanged


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**pet_id** | **str** |  | 
**status** | [**PetStatus**](PetStatus.md) |  | 

## Example

```python
from petstore_client.models.pet_status_changed import PetStatusChanged

# TODO update the JSON string below
json = "{}"
# create an instance of PetStatusChanged from a JSON string
pet_status_changed_instance = PetStatusChanged.from_json(json)
# print the JSON string representation of the object
print(PetStatusChanged.to_json())

# convert the object into a dict
pet_status_changed_dict = pet_status_changed_instance.to_dict()
# create an instance of PetStatusChanged from a dict
pet_status_changed_from_dict = PetStatusChanged.from_dict(pet_status_changed_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


