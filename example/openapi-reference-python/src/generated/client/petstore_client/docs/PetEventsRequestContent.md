# PetEventsRequestContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**ping** | [**PetPing**](PetPing.md) |  | [optional] 
**subscribe** | [**PetSubscription**](PetSubscription.md) |  | [optional] 

## Example

```python
from petstore_client.models.pet_events_request_content import PetEventsRequestContent

# TODO update the JSON string below
json = "{}"
# create an instance of PetEventsRequestContent from a JSON string
pet_events_request_content_instance = PetEventsRequestContent.from_json(json)
# print the JSON string representation of the object
print(PetEventsRequestContent.to_json())

# convert the object into a dict
pet_events_request_content_dict = pet_events_request_content_instance.to_dict()
# create an instance of PetEventsRequestContent from a dict
pet_events_request_content_from_dict = PetEventsRequestContent.from_dict(pet_events_request_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


