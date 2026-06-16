# PetProfileSummary


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **str** |  | 
**biography** | **str** |  | 
**pet_id** | **str** |  | 

## Example

```python
from petstore_client.models.pet_profile_summary import PetProfileSummary

# TODO update the JSON string below
json = "{}"
# create an instance of PetProfileSummary from a JSON string
pet_profile_summary_instance = PetProfileSummary.from_json(json)
# print the JSON string representation of the object
print(PetProfileSummary.to_json())

# convert the object into a dict
pet_profile_summary_dict = pet_profile_summary_instance.to_dict()
# create an instance of PetProfileSummary from a dict
pet_profile_summary_from_dict = PetProfileSummary.from_dict(pet_profile_summary_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


