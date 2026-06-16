# GetOrderResponseContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**order** | [**OrderDetail**](OrderDetail.md) |  | 

## Example

```python
from petstore_client.models.get_order_response_content import GetOrderResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of GetOrderResponseContent from a JSON string
get_order_response_content_instance = GetOrderResponseContent.from_json(json)
# print the JSON string representation of the object
print(GetOrderResponseContent.to_json())

# convert the object into a dict
get_order_response_content_dict = get_order_response_content_instance.to_dict()
# create an instance of GetOrderResponseContent from a dict
get_order_response_content_from_dict = GetOrderResponseContent.from_dict(get_order_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


