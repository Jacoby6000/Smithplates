# GetPetResponseContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**pet** | [**PetDetail**](PetDetail.md) |  | 

## Example

```python
from petstore_client.models.get_pet_response_content import GetPetResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of GetPetResponseContent from a JSON string
get_pet_response_content_instance = GetPetResponseContent.from_json(json)
# print the JSON string representation of the object
print(GetPetResponseContent.to_json())

# convert the object into a dict
get_pet_response_content_dict = get_pet_response_content_instance.to_dict()
# create an instance of GetPetResponseContent from a dict
get_pet_response_content_from_dict = GetPetResponseContent.from_dict(get_pet_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


