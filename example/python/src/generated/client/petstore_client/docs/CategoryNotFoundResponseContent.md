# CategoryNotFoundResponseContent

Requested category was not found.

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**message** | **str** |  | 

## Example

```python
from petstore_client.models.category_not_found_response_content import CategoryNotFoundResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of CategoryNotFoundResponseContent from a JSON string
category_not_found_response_content_instance = CategoryNotFoundResponseContent.from_json(json)
# print the JSON string representation of the object
print(CategoryNotFoundResponseContent.to_json())

# convert the object into a dict
category_not_found_response_content_dict = category_not_found_response_content_instance.to_dict()
# create an instance of CategoryNotFoundResponseContent from a dict
category_not_found_response_content_from_dict = CategoryNotFoundResponseContent.from_dict(category_not_found_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


