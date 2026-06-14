# OrderLineDetail


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **str** |  | 
**order_id** | **str** |  | 
**pet_id** | **str** |  | 
**quantity** | **float** |  | 
**unit_price_cents** | **float** |  | 
**fulfillment** | [**FulfillmentState**](FulfillmentState.md) |  | 

## Example

```python
from petstore_client.models.order_line_detail import OrderLineDetail

# TODO update the JSON string below
json = "{}"
# create an instance of OrderLineDetail from a JSON string
order_line_detail_instance = OrderLineDetail.from_json(json)
# print the JSON string representation of the object
print(OrderLineDetail.to_json())

# convert the object into a dict
order_line_detail_dict = order_line_detail_instance.to_dict()
# create an instance of OrderLineDetail from a dict
order_line_detail_from_dict = OrderLineDetail.from_dict(order_line_detail_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


