# UpdatePetResponseContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**updated** | **bool** |  | 

## Example

```python
from petstore_client.models.update_pet_response_content import UpdatePetResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of UpdatePetResponseContent from a JSON string
update_pet_response_content_instance = UpdatePetResponseContent.from_json(json)
# print the JSON string representation of the object
print(UpdatePetResponseContent.to_json())

# convert the object into a dict
update_pet_response_content_dict = update_pet_response_content_instance.to_dict()
# create an instance of UpdatePetResponseContent from a dict
update_pet_response_content_from_dict = UpdatePetResponseContent.from_dict(update_pet_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


