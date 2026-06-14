# OrderNotFoundResponseContent

Requested order was not found.

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**message** | **str** |  | 

## Example

```python
from petstore_client.models.order_not_found_response_content import OrderNotFoundResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of OrderNotFoundResponseContent from a JSON string
order_not_found_response_content_instance = OrderNotFoundResponseContent.from_json(json)
# print the JSON string representation of the object
print(OrderNotFoundResponseContent.to_json())

# convert the object into a dict
order_not_found_response_content_dict = order_not_found_response_content_instance.to_dict()
# create an instance of OrderNotFoundResponseContent from a dict
order_not_found_response_content_from_dict = OrderNotFoundResponseContent.from_dict(order_not_found_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


