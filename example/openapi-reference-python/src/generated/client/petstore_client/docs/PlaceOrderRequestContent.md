# PlaceOrderRequestContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**label** | **str** |  | 
**status** | [**OrderStatus**](OrderStatus.md) |  | 
**priority** | [**OrderPriority**](OrderPriority.md) |  | 

## Example

```python
from petstore_client.models.place_order_request_content import PlaceOrderRequestContent

# TODO update the JSON string below
json = "{}"
# create an instance of PlaceOrderRequestContent from a JSON string
place_order_request_content_instance = PlaceOrderRequestContent.from_json(json)
# print the JSON string representation of the object
print(PlaceOrderRequestContent.to_json())

# convert the object into a dict
place_order_request_content_dict = place_order_request_content_instance.to_dict()
# create an instance of PlaceOrderRequestContent from a dict
place_order_request_content_from_dict = PlaceOrderRequestContent.from_dict(place_order_request_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


