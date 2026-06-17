# GetCategoryResponseContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**category** | [**CategoryDetail**](CategoryDetail.md) |  | 

## Example

```python
from petstore_client.models.get_category_response_content import GetCategoryResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of GetCategoryResponseContent from a JSON string
get_category_response_content_instance = GetCategoryResponseContent.from_json(json)
# print the JSON string representation of the object
print(GetCategoryResponseContent.to_json())

# convert the object into a dict
get_category_response_content_dict = get_category_response_content_instance.to_dict()
# create an instance of GetCategoryResponseContent from a dict
get_category_response_content_from_dict = GetCategoryResponseContent.from_dict(get_category_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


