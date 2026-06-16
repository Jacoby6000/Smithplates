# CreatePetResponseContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **str** |  | 

## Example

```python
from petstore_client.models.create_pet_response_content import CreatePetResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of CreatePetResponseContent from a JSON string
create_pet_response_content_instance = CreatePetResponseContent.from_json(json)
# print the JSON string representation of the object
print(CreatePetResponseContent.to_json())

# convert the object into a dict
create_pet_response_content_dict = create_pet_response_content_instance.to_dict()
# create an instance of CreatePetResponseContent from a dict
create_pet_response_content_from_dict = CreatePetResponseContent.from_dict(create_pet_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


