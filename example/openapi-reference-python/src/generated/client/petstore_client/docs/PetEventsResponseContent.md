# PetEventsResponseContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**welcome** | [**PetWelcome**](PetWelcome.md) |  | [optional] 
**pong** | [**PetPong**](PetPong.md) |  | [optional] 
**status_changed** | [**PetStatusChanged**](PetStatusChanged.md) |  | [optional] 

## Example

```python
from petstore_client.models.pet_events_response_content import PetEventsResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of PetEventsResponseContent from a JSON string
pet_events_response_content_instance = PetEventsResponseContent.from_json(json)
# print the JSON string representation of the object
print(PetEventsResponseContent.to_json())

# convert the object into a dict
pet_events_response_content_dict = pet_events_response_content_instance.to_dict()
# create an instance of PetEventsResponseContent from a dict
pet_events_response_content_from_dict = PetEventsResponseContent.from_dict(pet_events_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


