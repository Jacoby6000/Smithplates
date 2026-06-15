# PetNotFoundResponseContent

Requested pet was not found.

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**message** | **str** |  | 

## Example

```python
from petstore_client.models.pet_not_found_response_content import PetNotFoundResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of PetNotFoundResponseContent from a JSON string
pet_not_found_response_content_instance = PetNotFoundResponseContent.from_json(json)
# print the JSON string representation of the object
print(PetNotFoundResponseContent.to_json())

# convert the object into a dict
pet_not_found_response_content_dict = pet_not_found_response_content_instance.to_dict()
# create an instance of PetNotFoundResponseContent from a dict
pet_not_found_response_content_from_dict = PetNotFoundResponseContent.from_dict(pet_not_found_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


